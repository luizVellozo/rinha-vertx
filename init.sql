CREATE TABLE IF NOT EXISTS PESSOA (
    ID VARCHAR(36),
    APELIDO VARCHAR(32) CONSTRAINT ID_PK PRIMARY KEY,
    NOME VARCHAR(100),
    NASCIMENTO CHAR(10),
    STACK VARCHAR(1024),
    BUSCA_TRGM TEXT GENERATED ALWAYS AS (
        LOWER(NOME || APELIDO || STACK)
    ) STORED
);

CREATE EXTENSION PG_TRGM;
CREATE INDEX CONCURRENTLY IF NOT EXISTS IDX_PESSOAS_BUSCA_TGRM ON PESSOA USING GIST (BUSCA_TRGM GIST_TRGM_OPS(SIGLEN=64));
