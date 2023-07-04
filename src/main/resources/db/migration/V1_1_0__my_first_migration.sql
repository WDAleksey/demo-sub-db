CREATE TABLE employees
(
    employee_id   NUMERIC       NOT NULL,
    first_name    VARCHAR(1000) NOT NULL,
    last_name     VARCHAR(1000) NOT NULL,
    date_of_birth DATE,
    phone_number  VARCHAR(1000) NOT NULL,
    CONSTRAINT employees_pk PRIMARY KEY (employee_id)
);

INSERT INTO employees (employee_id, first_name, last_name, date_of_birth, phone_number)
VALUES (1, 'Aleksey', 'Yufros', '1990-09-28'::date, '+79202233400'),
       (2, 'Irina', 'Yufros', '1990-08-07'::date, '+79529581648');