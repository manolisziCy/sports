CREATE TABLE events (
  id BIGSERIAL PRIMARY KEY NOT NULL,
  actor TEXT,
  recipient TEXT,
  event_name TEXT,
  event_time TIMESTAMP WITH TIME ZONE,
  error_code TEXT,
  parameters JSONB,
  ip TEXT,
  agent Text
);

CREATE INDEX events_actor_idx ON events (actor);
CREATE INDEX events_event_name_idx ON events (event_name);
CREATE INDEX events_event_time_idx ON events (event_time);
CREATE INDEX events_error_code_idx ON events (error_code);
CREATE INDEX events_ip_idx ON events (ip);

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY NOT NULL,
  username TEXT UNIQUE NOT NULL,
  password TEXT NOT NULL,
  status INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  last_login_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  email_verified_at TIMESTAMP WITH TIME ZONE,
  email_verification_sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  password_reset_email_sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX users_username_idx ON users(username);
CREATE INDEX users_status_idx ON users(status);
CREATE INDEX users_last_login_at_idx ON users(last_login_at);

CREATE TABLE configuration (
   key TEXT PRIMARY KEY NOT NULL,
   value TEXT
);

CREATE TABLE pending_user_emails (
   user_id BIGINT PRIMARY KEY NOT NULL REFERENCES users (id) ON DELETE CASCADE,
   actor TEXT,
   action TEXT NOT NULL,
   lang TEXT NOT NULL DEFAULT 'en',
   retries int,
   next_try TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE submitted_user_emails (
   user_id BIGINT,
   action TEXT NOT NULL,
   submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE FUNCTION proc_submitted_user_emails() RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO submitted_user_emails (user_id, submitted_at, action) (SELECT OLD.user_id, now(), OLD.action);
    RETURN OLD;
END;
$$;
CREATE TRIGGER tri_a_submitted_user_emails AFTER DELETE ON pending_user_emails FOR EACH ROW
EXECUTE PROCEDURE proc_submitted_user_emails();

INSERT INTO users (id, username, password, status)
VALUES (0, 'support@cytech.gr', '$s0$10101$cb5RJUp+lnfuxGNM9EvO4w==$pc1JCD0K+/7QlZ4PLD1yUdtSfxBxSs1mDhnmaJJG8yM=', 1);