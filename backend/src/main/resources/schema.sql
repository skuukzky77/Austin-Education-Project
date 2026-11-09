CREATE TABLE IF NOT EXISTS users (
 id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, name TEXT NOT NULL,
 role TEXT NOT NULL CHECK(role IN ('COUNSELOR','TEACHER')), password_hash TEXT NOT NULL,
 enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1))
);
CREATE TABLE IF NOT EXISTS students (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'CONSULTING',
 counselor_id INTEGER NOT NULL REFERENCES users(id), created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS contacts (id INTEGER PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS student_contacts (
 student_id INTEGER NOT NULL REFERENCES students(id), contact_id INTEGER NOT NULL REFERENCES contacts(id),
 relationship TEXT NOT NULL, is_primary INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(student_id,contact_id)
);
CREATE TABLE IF NOT EXISTS lessons (
 id INTEGER PRIMARY KEY, title TEXT NOT NULL, teacher_id INTEGER NOT NULL REFERENCES users(id),
 starts_at INTEGER NOT NULL, ends_at INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'SCHEDULED',
 CHECK (starts_at < ends_at)
);
CREATE TABLE IF NOT EXISTS lesson_students (
 lesson_id INTEGER NOT NULL REFERENCES lessons(id), student_id INTEGER NOT NULL REFERENCES students(id),
 PRIMARY KEY(lesson_id, student_id)
);
CREATE TABLE IF NOT EXISTS trials (
 id INTEGER PRIMARY KEY, student_id INTEGER NOT NULL REFERENCES students(id),
 teacher_id INTEGER NOT NULL REFERENCES users(id), starts_at INTEGER NOT NULL, ends_at INTEGER NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('SCHEDULED','COMPLETED','CANCELLED')),
 result TEXT, notes TEXT, no_followup_reason TEXT, created_at INTEGER NOT NULL,
 CHECK(starts_at < ends_at),
 CHECK(status != 'COMPLETED' OR (result IS NOT NULL AND length(trim(result)) > 0))
);
CREATE UNIQUE INDEX IF NOT EXISTS one_scheduled_trial_per_student ON trials(student_id) WHERE status = 'SCHEDULED';
CREATE TABLE IF NOT EXISTS followups (
 id INTEGER PRIMARY KEY, student_id INTEGER NOT NULL REFERENCES students(id), trial_id INTEGER NOT NULL UNIQUE REFERENCES trials(id),
 counselor_id INTEGER NOT NULL REFERENCES users(id), due_at INTEGER NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('OPEN','DONE')), action TEXT NOT NULL, completion_note TEXT, completed_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_trials_time ON trials(starts_at,ends_at,status);
CREATE INDEX IF NOT EXISTS idx_followups_owner_status ON followups(counselor_id,status,due_at);
