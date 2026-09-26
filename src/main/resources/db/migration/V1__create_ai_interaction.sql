-- FR-008 / SPEC 51-55: AI observability. Contains masked content only.
-- Unknown values (token usage not reported, failed calls, not yet completed) are NULL rather than invented zeros.

-- One advisor request (POST /api/chat). Inserted when the request starts; completed_at, status and
-- duration_millis are NULL while it is running and set together when it completes.
CREATE TABLE advisor_interaction (
    interaction_id          UUID         PRIMARY KEY,
    started_at              TIMESTAMPTZ  NOT NULL,
    completed_at            TIMESTAMPTZ,
    masked_advisor_message  TEXT,
    masked_final_response   TEXT,
    status                  VARCHAR(32),
    duration_millis         BIGINT
);

CREATE INDEX advisor_interaction_started_at_idx ON advisor_interaction (started_at);

-- One physical call of the external chat model ("LLM call").
CREATE TABLE ai_interaction (
    id                      UUID           PRIMARY KEY,
    advisor_interaction_id  UUID           REFERENCES advisor_interaction (interaction_id),
    interaction_timestamp   TIMESTAMPTZ    NOT NULL,
    model_identifier        TEXT,
    status                  VARCHAR(16)    NOT NULL,
    error_type              TEXT,
    masked_user_prompt      TEXT,
    masked_model_response   TEXT,
    input_token_count       INTEGER,
    output_token_count      INTEGER,
    estimated_cost          NUMERIC(20, 10),
    duration_millis         BIGINT         NOT NULL,
    tool_names              TEXT[]         NOT NULL
);

CREATE INDEX ai_interaction_timestamp_idx ON ai_interaction (interaction_timestamp);
CREATE INDEX ai_interaction_advisor_interaction_idx ON ai_interaction (advisor_interaction_id);

-- One actual execution of an agent tool. No tool arguments or results are stored.
CREATE TABLE tool_invocation (
    tool_invocation_id      UUID         PRIMARY KEY,
    advisor_interaction_id  UUID         NOT NULL REFERENCES advisor_interaction (interaction_id),
    tool_name               TEXT         NOT NULL,
    started_at              TIMESTAMPTZ  NOT NULL,
    duration_millis         BIGINT       NOT NULL,
    status                  VARCHAR(16)  NOT NULL
);

CREATE INDEX tool_invocation_advisor_interaction_idx ON tool_invocation (advisor_interaction_id);
