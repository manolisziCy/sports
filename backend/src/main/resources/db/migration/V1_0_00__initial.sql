CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY NOT NULL,
  username TEXT UNIQUE NOT NULL,
  password TEXT NOT NULL,
  status INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  last_login_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  -- contains: sport id (id of the table sports), name, level (int), equipment (bool)
  sports JSONB,
  -- contains the id of the activity of the activity table
  activities JSONB DEFAULT NULL,
  -- contains the id of the users that are friend of his
  friends JSONB DEFAULT NULL,
  checkpoints REAL DEFAULT 0.0,
  coordinate_x double precision NOT NULL,
  coordinate_y double precision NOT NULL
);

CREATE INDEX users_username_idx ON users(username);
CREATE INDEX users_status_idx ON users(status);
CREATE INDEX users_last_login_at_idx ON users(last_login_at);
CREATE INDEX users_checkpoints_idx ON users(checkpoints);

CREATE TABLE activities (
  id BIGSERIAL PRIMARY KEY NOT NULL,
  sport_id BIGSERIAL NOT NULL,
  name TEXT NOT NULL,
  players_level_min INTEGER,
  players_level_max INTEGER,
  -- friendly - coaching - tournament
  type INTEGER NOT NULL,
  -- team, user id
  participants JSONB NOT NULL,
  own_equipment BOOLEAN NOT NULL DEFAULT true,
  -- weather the game will be open for users to join or with invitation
  public BOOLEAN NOT NULL DEFAULT true,
  max_number_of_players INTEGER NOT NULL,
  date TIMESTAMP WITH TIME ZONE NOT NULL,
  comments TEXT NOT NULL
);

CREATE INDEX activities_name_idx ON activities(name);
CREATE INDEX activities_type_idx ON activities(type);
CREATE INDEX activities_public_idx ON activities(public);
CREATE INDEX activities_date_idx ON activities(date);

CREATE TABLE sports (
  id BIGSERIAL PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  -- 0 not, 1 is a team sport, 2 is both
  team_sport INTEGER NOT NULL
);

INSERT INTO sports (name, team_sport)
VALUES ('basketball', 1),
       ('skateboard', 0),
       ('climbing', 0),
       ('cycling', 0),
       ('fishing', 0),
       ('football', 1),
       ('golf', 0),
       ('parkour', 0),
       ('tennis', 2),
       ('running', 0),
       ('swimming', 0);

CREATE TABLE configuration (
   key TEXT PRIMARY KEY NOT NULL,
   value TEXT
);

INSERT INTO users (id, username, password, status, coordinate_x, coordinate_y)
VALUES (0, 'support@cytech.gr', '$s0$10101$cb5RJUp+lnfuxGNM9EvO4w==$pc1JCD0K+/7QlZ4PLD1yUdtSfxBxSs1mDhnmaJJG8yM=', 1, 0.0, 0.0);