package uk.gov.homeoffice.digital.sas.jparest;

import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;

import com.fasterxml.jackson.databind.util.StdDateFormat;

import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.Literal;
import org.springframework.expression.spel.ast.MethodReference;
import org.springframework.expression.spel.ast.OpAnd;
import org.springframework.expression.spel.ast.OpEQ;
import org.springframework.expression.spel.ast.OpGE;
import org.springframework.expression.spel.ast.OpGT;
import org.springframework.expression.spel.ast.OpLE;
import org.springframework.expression.spel.ast.OpLT;
import org.springframework.expression.spel.ast.OpOr;
import org.springframework.expression.spel.ast.OperatorMatches;
import org.springframework.expression.spel.ast.OperatorNot;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.util.Assert;
import org.springframework.web.bind.WebDataBinder;

/**
 * Used to convert SpelExpression into a JPA predicate
 */
public class SpelExpressionToPredicateConverter  {

    private final static Logger LOGGER = Logger.getLogger(SpelExpressionToPredicateConverter.class.getName());

    private static WebDataBinder binder = initBinder();

    // TODO: Maybe need to use DI to reuse a single instance
    private static WebDataBinder initBinder() {
        WebDataBinder binder = new WebDataBinder(null);

        StdDateFormat dateFormat2 = new StdDateFormat();
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat2, true));

        return binder;
    }
    
    /**
     * Converts SpelExpression to a JPA predicate
     * @param from The source SpelExpression
     * @param builder The CriteriaBuilder to use to create the predicate, must not be {@literal null}.
     * @param root must not be {@literal null}.
     * @return a {@link javax.persistence.criteria.Predicate}.
     */
    public static Predicate convert(SpelExpression from, CriteriaBuilder builder, From<?, ?> root) {
        if (from == null) {
            LOGGER.fine("Nothing to convert, SpelExpression is null");
            return null;
        }

		Assert.notNull(builder, "builder must not be null!");
        Assert.notNull(root, "root must not be null!");

        LOGGER.fine("Converting SpelExpression AST to predicate");
        return getPredicate(from.getAST(), builder, root);
    }

    /*  Builds predicate recursively. 
     *  Allows AND/OR/NOT as logical operators
     *  Allows Field comparison with literals or other fields
     *  Allows methods where method is supported by {@link #getMethodPredicate(SpelNode, CriteriaBuilder, From<?, ?>)
     */
    private static Predicate getPredicate(SpelNode node, CriteriaBuilder builder, From<?, ?> root) {
        Predicate predicate = null;

        // Handle logical operators
        if (OpOr.class.isInstance(node)) {
            Predicate x = getPredicate(node.getChild(0), builder, root);
            Predicate y = getPredicate(node.getChild(1), builder, root);
            predicate = builder.or(x, y);
        } else if (OpAnd.class.isInstance(node)) {
            Predicate x = getPredicate(node.getChild(0), builder, root);
            Predicate y = getPredicate(node.getChild(1), builder, root);
            predicate = builder.and(x, y);
        } else if (OperatorNot.class.isInstance(node)) {
            Predicate x = getPredicate(node.getChild(0), builder, root);
            predicate = builder.not(x);
        } else if (MethodReference.class.isInstance(node)) {
            // delegate method reference to getMethodPredicate 
            predicate = getMethodPredicate((MethodReference)node, builder, root);
        } else {
            // At this point we are looking for "property {operator} property/literal"
            // so we can only handle 2 children the left side and the right side
            // of the expression
            if (node.getChildCount() == 2) {
                
                // Left side must be a field
                SpelNode leftNode = node.getChild(0);
                if (!PropertyOrFieldReference.class.isAssignableFrom(leftNode.getClass())) {
                    LOGGER.severe("Left hand side was not assignable to PropertyOrFieldReference");
                    throw new InvalidFilterException("Left hand side must be a field");
                }
                PropertyOrFieldReference fieldReference = (PropertyOrFieldReference)leftNode;
                // TODO: Better error handling for left and right side to return bad request and report invalid field 
                Path<Comparable<Object>> field = root.get(fieldReference.getName());
                Class<?> clazz = field.getJavaType();

                // Get the right side
                SpelNode rightNode = node.getChild(1);
                // handle field comparison
                if (PropertyOrFieldReference.class.isInstance(rightNode)) {
                    Path<Comparable<Object>> rightField = root.get(((PropertyOrFieldReference)rightNode).getName());
                    if (OpEQ.class.isInstance(node)) {
                        predicate = builder.equal(field, rightField);
                    } else if (OpGE.class.isInstance(node)) {
                        predicate = builder.greaterThanOrEqualTo(field,rightField);
                    } else if (OpGT.class.isInstance(node)) {
                        predicate = builder.greaterThan(field, rightField);
                    } else if (OpLE.class.isInstance(node)) {
                        predicate = builder.lessThanOrEqualTo(field, rightField);
                    } else if (OpLT.class.isInstance(node)) {
                        predicate = builder.lessThan(field,  rightField);
                    } else {
                        // TODO: Probably should support not equal here
                        LOGGER.severe("Left hand side and right hand side where properties but the operator was not supported");
                        throw new InvalidFilterException("Operator not valid between two fields. " + node.toStringAST());
                    }
                // handle literal comparison
                } else if (Literal.class.isAssignableFrom(rightNode.getClass())) {
                    Object rightValue = convertTo(((Literal)rightNode).getLiteralValue().getValue(), clazz);
                    @SuppressWarnings("unchecked")
                    Comparable<Object> comparableValue = (Comparable<Object>)rightValue;
                    
                    if (OpEQ.class.isInstance(node)) {
                        predicate = builder.equal(field, comparableValue);
                    } else if (OpGE.class.isInstance(node)) {
                        predicate = builder.greaterThanOrEqualTo(field, comparableValue);
                    } else if (OpGT.class.isInstance(node)) {
                        predicate = builder.greaterThan(field, comparableValue);
                    } else if (OpLE.class.isInstance(node)) {
                        predicate = builder.lessThanOrEqualTo(field, comparableValue);                    
                    } else if (OpLT.class.isInstance(node)) {
                        predicate = builder.lessThan(field,  comparableValue);
                    } else if (OperatorMatches.class.isInstance(node)) {
                        // TODO: Check if clazz is a string
                        predicate = builder.like(field.as(String.class), (String)rightValue);
                    } else {
                        throw new InvalidFilterException("TODO: Describe error");
                    }
                } else {
                    throw new InvalidFilterException("Right hand side must be a literal or a field");
                }
            } else {
                throw new InvalidFilterException("Unknown expression");
            }
        }
        return predicate;
    }

    /**
     * Converts Spel Method reference to predicate
     * Possible methods are
     *  <ul><li>In
     *  <li>Between</ul>
     */
    private static Predicate getMethodPredicate(MethodReference node, CriteriaBuilder builder, From<?, ?> root) {

        // To handle a method the first argument must be the field reference
        SpelNode firstArg = node.getChild(0);
        if (!PropertyOrFieldReference.class.isAssignableFrom(firstArg.getClass())) {
            throw new InvalidFilterException("First argument must be a field");
        }

        PropertyOrFieldReference fieldReference = (PropertyOrFieldReference)firstArg;
        MethodReference methodReference = (MethodReference)node;
        Path<Comparable<Object>> field = root.get(fieldReference.getName());
        Class<?> clazz = field.getJavaType();

        Predicate predicate;
        Path<Comparable<Object>> comparableField;
        Comparable<Object>[] args;

        // Create the appropriate predicate
        switch(methodReference.getName()) {
            case "Between":
                comparableField = root.get(fieldReference.getName());
                args = getLiteralValues(node, 1, clazz);
                predicate = builder.between(comparableField, args[0], args[1]);
                break;
            case "In":
                comparableField = root.get(fieldReference.getName());
                args = getLiteralValues(node, 1, clazz);
                predicate = field.in((Object[])args);
                break;
            default:
                // Throw if the method is not handled
                throw new InvalidFilterException("Unrecognised method " + methodReference.getName());
        }
        return predicate;
    }

    /**
     * Gets literal values from the spel expression as the given type
     */
    private static Comparable<Object>[] getLiteralValues(SpelNode node, int startPos, Class<?> clazz) {
        ArrayList<Comparable<Object>> items = new ArrayList<Comparable<Object>>();
        for (int i = startPos; i < node.getChildCount(); i++) {
            @SuppressWarnings("unchecked")
            Comparable<Object> rightValue = (Comparable<Object>)convertTo(((Literal)node.getChild(i)).getLiteralValue().getValue(), clazz);
            items.add((Comparable<Object>)rightValue);
        }
        @SuppressWarnings("unchecked")
        Comparable<Object>[] result = new Comparable[node.getChildCount() - 1];
        return items.toArray(result);
    }

    private static <Y> Y convertTo(Object value, Class<Y> clazz) {
        return binder.convertIfNecessary(value, clazz);
    }

}