-- Apply only to the designated development/test Oracle schema before starting this version.
-- Safe to rerun: adds only missing columns and retains existing rows.
DECLARE
  n NUMBER;
  column_type VARCHAR2(128);
  column_length NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM user_tables WHERE table_name = 'RESERVATIONS';
  IF n <> 1 THEN
    RAISE_APPLICATION_ERROR(-20001, 'RESERVATIONS table is missing in the current schema');
  END IF;
  SELECT COUNT(*) INTO n FROM user_tab_columns
    WHERE table_name = 'RESERVATIONS' AND column_name = 'CANCELLED_AT';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE reservations ADD (cancelled_at TIMESTAMP)';
  END IF;
  SELECT data_type INTO column_type FROM user_tab_columns
    WHERE table_name = 'RESERVATIONS' AND column_name = 'CANCELLED_AT';
  IF column_type <> 'DATE' AND column_type NOT LIKE 'TIMESTAMP(%)' THEN
    RAISE_APPLICATION_ERROR(-20002, 'CANCELLED_AT must be DATE or plain TIMESTAMP');
  END IF;
  SELECT COUNT(*) INTO n FROM user_tab_columns
    WHERE table_name = 'RESERVATIONS' AND column_name = 'CANCELLED_BY';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE reservations ADD (cancelled_by VARCHAR2(16))';
  END IF;
  SELECT data_type, char_length INTO column_type, column_length FROM user_tab_columns
    WHERE table_name = 'RESERVATIONS' AND column_name = 'CANCELLED_BY';
  IF column_type NOT IN ('VARCHAR2', 'NVARCHAR2', 'CHAR', 'NCHAR') OR column_length < 7 THEN
    RAISE_APPLICATION_ERROR(-20003, 'CANCELLED_BY must hold STUDENT');
  END IF;
END;
/
