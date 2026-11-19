ALTER TABLE activity
    ADD COLUMN check_in_code CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT uk_activity_check_in_code UNIQUE (check_in_code);
