INSERT INTO dummyEntityA (id) VALUES
    (1),
    (2);

INSERT INTO dummyEntityB (id) VALUES
    (1),
    (2);

INSERT INTO dummyEntityC (id, description) VALUES
    (1, 'Dummy Entity C number 1'),
    (2, 'Dummy Entity C number 2');

INSERT INTO dummyEntityA_dummyEntityB (dummyEntityA, dummyEntityB) VALUES
    (1, 1),
    (1, 2),
    (2, 2);
