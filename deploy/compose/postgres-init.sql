-- Each service owns its own database; neither can read the other's tables.
CREATE USER payments WITH PASSWORD 'payments';
CREATE DATABASE payments OWNER payments;

CREATE USER notifications WITH PASSWORD 'notifications';
CREATE DATABASE notifications OWNER notifications;
