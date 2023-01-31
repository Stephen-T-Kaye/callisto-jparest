package uk.gov.homeoffice.digital.sas.jparest.entityutils.testentities;

import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import uk.gov.homeoffice.digital.sas.jparest.annotation.Resource;
import uk.gov.homeoffice.digital.sas.jparest.models.BaseEntity;

@Resource(path = "dummyEntityBs")
@Entity(name = "dummyEntityB")
@EqualsAndHashCode(callSuper = false)
public class DummyEntityB extends BaseEntity {

    @EqualsAndHashCode.Exclude
    @ManyToMany(mappedBy = "dummyEntityBSet")
    private Set<DummyEntityA> dummyEntityASet;

}
