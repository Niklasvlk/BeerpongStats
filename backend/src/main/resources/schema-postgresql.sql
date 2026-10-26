-- Spielernamen sind unabhängig von der Groß-/Kleinschreibung eindeutig (läuft nach dem Hibernate-Schema-Update)
CREATE UNIQUE INDEX IF NOT EXISTS ux_player_name_lower ON player (lower(name));
