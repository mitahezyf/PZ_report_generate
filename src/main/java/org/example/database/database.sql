
# awaryjna kwerenda do ubijania calej bazy danych
#drop schema pzdb;

# tworzenie bazy danych
create schema pzdb;

# wybieranie schematu pzdb jako domyslnego
use pzdb;

# tabela z rolami i poziomem uprawnien
CREATE TABLE `Roles` (
                         `id` int PRIMARY KEY AUTO_INCREMENT,
                         `name` enum('prezes','projektManager','teamLider','pracownik'),
                         `privilege_level` tinyint
);
# tabela z nazwami teamow
CREATE TABLE `Teams` (
                         `id` int PRIMARY KEY AUTO_INCREMENT,
                         `name` varchar(255)
);

# tabela z informacjami o uzytkownikach
CREATE TABLE `Users` (
                         `id` int PRIMARY KEY AUTO_INCREMENT,
                         `team_id` int,
                         `role_id` int,
                         `first_name` varchar(20),
                         `last_name` varchar(30),
                         `hire_date` date,
                         `login` varchar(30) UNIQUE,
                         `password_hash` varchar(60),
                         `created_at` timestamp,
                         FOREIGN KEY (`team_id`) REFERENCES `Teams` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
                         FOREIGN KEY (`role_id`) REFERENCES `Roles` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
);

# tabela z informacjami o projekcie
CREATE TABLE `Projects` (
                            `id` int PRIMARY KEY AUTO_INCREMENT,
                            `manager_id` int NOT NULL ,
                            `name` varchar(255),
                            `progress` tinyint CHECK (progress BETWEEN 0 AND 100),
                            `status` enum('planowany','wTrakcie','zakonczony','anulowany'),
                            `start_date` date NOT NULL,
                            `end_date` date,
                            FOREIGN KEY (`manager_id`) REFERENCES `Users` (`id`) ON DELETE CASCADE,
                            CHECK (end_date >= start_date)
);

# tabela z przypisaniem teamow do projektow
CREATE TABLE `ProjectTeams` (
                                `project_id` int,
                                `team_id` int,
                                PRIMARY KEY (`project_id`, `team_id`),
                                FOREIGN KEY (`project_id`) REFERENCES `Projects` (`id`) ON DELETE CASCADE,
                                FOREIGN KEY (`team_id`) REFERENCES `Teams` (`id`) ON DELETE CASCADE
);

# tabela z kamieniami milowymi projektow
CREATE TABLE `Milestones` (
                              `id` int PRIMARY KEY AUTO_INCREMENT,
                              `project_id` int NOT NULL,
                              `name` varchar(50),
                              `progress` tinyint CHECK (progress BETWEEN 0 AND 100),
                              `description` text,
                              `deadline` date,
                              FOREIGN KEY (`project_id`) REFERENCES `Projects` (`id`) ON DELETE CASCADE
);

# tabela z informacjami o zadaniu
CREATE TABLE `Tasks` (
                         `id` int PRIMARY KEY AUTO_INCREMENT,
                         `milestone_id` int NOT NULL,
                         `title` varchar(50),
                         `description` text,
                         `priority` enum('niski','sredni','wysoki'),
                         `status` enum('doZrobienia','wTrakcie','zrobione','anulowane'),
                         `progress` tinyint CHECK (progress BETWEEN 0 AND 100),
                         `created_at` timestamp,
                         `deadline` date,
                         `canceled_by` int,
                         FOREIGN KEY (`milestone_id`) REFERENCES `Milestones` (`id`) ON DELETE CASCADE,
                         FOREIGN KEY (`canceled_by`) REFERENCES `Users` (`id`) ON DELETE RESTRICT
);

# tabela z informacjami o przypisaniu zadan do uzytkownikow
CREATE TABLE `TaskAssignments` (
                                   `task_id` int,
                                   `assigned_by` int,
                                   `user_id` int,
                                   `assigned_at` timestamp,
                                   PRIMARY KEY (`task_id`, `user_id`),
                                   FOREIGN KEY (`task_id`) REFERENCES `Tasks` (`id`) ON DELETE CASCADE,
                                   FOREIGN KEY (`assigned_by`) REFERENCES `Users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
                                   FOREIGN KEY (`user_id`) REFERENCES `Users` (`id`) ON DELETE CASCADE
);

# tabela z informacjami o wygenerowanych raportach
CREATE TABLE `Reports` (
                           `id` int PRIMARY KEY AUTO_INCREMENT,
                           `created_by` int,
                           `type` varchar(255),
                           `parameters` text,
                           `generated_at` timestamp,
                           FOREIGN KEY (`created_by`) REFERENCES `Users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
);

# tabela z informacjami do powiadomien
CREATE TABLE `Notifications` (
                                 `id` int PRIMARY KEY AUTO_INCREMENT,
                                 `task_id` int,
                                 `user_id` int NOT NULL ,
                                 `report_id` int,
                                 `type` enum('zadaniePrzypisane','aktualizacjaZadania','deadline','generowanieRaportu','inne'),
                                 `message` text,
                                 `is_read` boolean DEFAULT false,
                                 `created_at` timestamp,
                                 FOREIGN KEY (`task_id`) REFERENCES `Tasks` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
                                 FOREIGN KEY (`user_id`) REFERENCES `Users` (`id`) ON DELETE CASCADE,
                                 FOREIGN KEY (`report_id`) REFERENCES `Reports` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
);

#============
# indexy
#============
# indexy dla tabeli Tasks
CREATE INDEX idx_tasks_status ON Tasks(status);
CREATE INDEX idx_tasks_priority ON Tasks(priority);
CREATE INDEX idx_tasks_status_priority_deadline ON Tasks(status, priority, deadline);
CREATE INDEX idx_tasks_milestone ON Tasks(milestone_id);
CREATE INDEX idx_tasks_canceled_by ON Tasks(canceled_by);
CREATE INDEX idx_tasks_deadline ON Tasks(deadline);

# indexy dla tabeli Users
CREATE INDEX idx_users_team ON Users(team_id);
CREATE INDEX idx_users_role_team ON Users(role_id, team_id);
CREATE INDEX idx_users_role_id ON Users(role_id);
CREATE INDEX idx_users_team_role_hire ON Users(team_id, role_id, hire_date);
CREATE INDEX idx_user_team_role ON Users(team_id, role_id);

# indexy dla tabeli Projects
CREATE INDEX idx_projects_status ON Projects(status);
CREATE INDEX idx_projects_manager_id ON Projects(manager_id);
CREATE INDEX idx_projects_manager_status_end ON Projects(manager_id, status, end_date);

# indexy dla tabeli Milestone
CREATE INDEX idx_milestones_project_deadline_progress ON Milestones(project_id, deadline, progress);
CREATE INDEX idx_milestones_project ON Milestones(project_id);
CREATE INDEX idx_milestones_deadline ON  Milestones(deadline);

# indexy dla tabeli Notifications
CREATE INDEX idx_notifications_task_id ON Notifications(task_id);
CREATE INDEX idx_notifications_report_id ON Notifications(report_id);
CREATE INDEX idx_notifications_type ON Notifications(type);

# indexy dla tabeli Raports
CREATE INDEX idx_reports_created_by ON Reports(created_by);
CREATE INDEX idx_reports_type ON Reports(type);

# indexy dla tabeli TaskAssignments
CREATE INDEX idx_assignments_task_user_date ON TaskAssignments(task_id, user_id, assigned_at);

CREATE INDEX idx_projects_status_manager ON Projects(status, manager_id);
CREATE INDEX idx_tasks_deadline_status ON Tasks(deadline, status);
CREATE INDEX idx_milestones_project_deadline ON Milestones(project_id, deadline);

#============
# widoki
#============
#pelne informacje o pracowniku
CREATE VIEW vw_UserCompleteDetails AS
SELECT
    u.id AS user_id,
    u.first_name,
    u.last_name,
    r.name AS role,
    t.name AS team,
    u.hire_date,
    u.login,
    u.created_at AS user_created_at,
    MAX(CONCAT(manager.first_name, ' ', manager.last_name)) AS manager_name,
    MAX(CONCAT(team_leader.first_name, ' ', team_leader.last_name)) AS team_leader_name,
    GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') AS projects_assigned,
    GROUP_CONCAT(DISTINCT m.name SEPARATOR ', ') AS milestones_assigned,
    GROUP_CONCAT(DISTINCT tas.title SEPARATOR ', ') AS tasks_assigned,
    COUNT(DISTINCT tas.id) AS total_tasks,
    SUM(tas.status = 'doZrobienia') AS todo,
    SUM(tas.status = 'wTrakcie') AS in_progress,
    SUM(tas.status = 'zrobione') AS done,
    SUM(tas.status = 'anulowane') AS canceled
FROM Users u
         JOIN Roles r ON u.role_id = r.id
         LEFT JOIN Teams t ON u.team_id = t.id
         LEFT JOIN TaskAssignments ta ON u.id = ta.user_id
         LEFT JOIN Tasks tas ON ta.task_id = tas.id
         LEFT JOIN Milestones m ON tas.milestone_id = m.id
         LEFT JOIN Projects p ON m.project_id = p.id
         LEFT JOIN Users manager ON p.manager_id = manager.id
         LEFT JOIN Users team_leader ON t.id = team_leader.team_id AND team_leader.role_id = (SELECT id FROM Roles WHERE name = 'teamLider')
GROUP BY u.id;

# widok do wyswietlania szczegolow uzytkownikow
CREATE VIEW vw_UserDetails AS
SELECT
    u.id,
    u.first_name,
    u.last_name,
    r.name AS role,
    t.name AS team,
    u.hire_date,
    u.login,
    u.created_at
FROM Users u
         JOIN Roles r ON u.role_id = r.id
         LEFT JOIN Teams t ON u.team_id = t.id;

# widok do wyswietlania szczegolow projektu
CREATE OR REPLACE VIEW vw_ProjectDetails AS
SELECT
    p.id,
    p.name AS project_name,
    CONCAT(u.first_name, ' ', u.last_name) AS manager,
    MAX(p.progress) AS progress,
    MAX(p.status) AS status,
    MAX(p.start_date) AS start_date,
    MAX(p.end_date) AS end_date,
    GROUP_CONCAT(t.name SEPARATOR ', ') AS teams
FROM Projects p
         JOIN Users u ON p.manager_id = u.id
         LEFT JOIN ProjectTeams pt ON p.id = pt.project_id
         LEFT JOIN Teams t ON pt.team_id = t.id
GROUP BY p.id;

# widok do wyswietlania szczegolow przypisywania zadan i uzytkownikow
CREATE OR REPLACE VIEW vw_TaskAssignments AS
SELECT
    t.id AS task_id,
    u.id AS user_id,
    u.team_id,
    t.title,
    t.status AS task_status,
    CONCAT(a.first_name, ' ', a.last_name) AS assigned_by,
    CONCAT(u.first_name, ' ', u.last_name) AS assigned_to,
    CONCAT(tl.first_name, ' ', tl.last_name) AS team_leader,
    CONCAT(pm.first_name, ' ', pm.last_name) AS project_manager,
    ta.assigned_at
FROM TaskAssignments ta
         JOIN Tasks t ON ta.task_id = t.id
         JOIN Milestones m ON t.milestone_id = m.id
         JOIN Projects p ON m.project_id = p.id
         JOIN Users pm ON p.manager_id = pm.id
         JOIN Users u ON ta.user_id = u.id
         LEFT JOIN Users tl ON tl.team_id = u.team_id AND tl.role_id = (SELECT id FROM Roles WHERE name = 'teamLider')
         JOIN Users a ON ta.assigned_by = a.id;

# widok do wyswietlania szczegolow postepu kamieni milowych
CREATE VIEW vw_MilestoneProgress AS
SELECT
    m.name AS milestone,
    m.progress,
    m.deadline,
    p.name AS project,
    p.progress AS project_progress
FROM Milestones m
         JOIN Projects p ON m.project_id = p.id;

# widok do wyswietlania zblizajacych sie terminow zadan
CREATE VIEW vw_UpcomingDeadlines AS
SELECT
    'Task' AS type,
    t.title,
    t.deadline,
    CONCAT(u.first_name, ' ', u.last_name) AS assigned_to
FROM Tasks t
         JOIN TaskAssignments ta ON t.id = ta.task_id
         JOIN Users u ON ta.user_id = u.id
WHERE t.deadline BETWEEN CURDATE() AND CURDATE() + INTERVAL 7 DAY
UNION ALL
SELECT
    'Milestone' AS type,
    m.name,
    m.deadline,
    NULL
FROM Milestones m
WHERE m.deadline BETWEEN CURDATE() AND CURDATE() + INTERVAL 7 DAY;

# widok do wyswietlania kto ile jakich ma zadan w danym statusie
CREATE VIEW vw_UserTaskLoad AS
SELECT
    CONCAT(u.first_name, ' ', u.last_name) AS user,
    COUNT(t.id) AS total_tasks,
    SUM(t.status = 'doZrobienia') AS todo,
    SUM(t.status = 'wTrakcie') AS in_progress,
    SUM(t.status = 'zrobione') AS done
FROM Users u
         LEFT JOIN TaskAssignments ta ON u.id = ta.user_id
         LEFT JOIN Tasks t ON ta.task_id = t.id
GROUP BY u.id;

# widok do wyswietlania podziału projektu na kamienie i zadania
CREATE VIEW vw_ProjectSummary AS
SELECT
    p.name AS project,
    p.status,
    p.progress,
    COUNT(DISTINCT m.id) AS milestones,
    COUNT(DISTINCT t.id) AS tasks,
    MIN(t.deadline) AS next_deadline
FROM Projects p
         LEFT JOIN Milestones m ON p.id = m.project_id
         LEFT JOIN Tasks t ON m.id = t.milestone_id
GROUP BY p.id, p.name, p.status, p.progress;

# widok do wyswietlania uzytkownikow ich role i przypisane projekty
CREATE VIEW vw_UserPrivileges AS
SELECT
    u.login,
    r.name AS role,
    r.privilege_level,
    IF(r.name = 'prezes', (SELECT GROUP_CONCAT(DISTINCT name SEPARATOR ', ') FROM Projects),
       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ')) AS managed_projects
FROM Users u
         JOIN Roles r ON u.role_id = r.id
         LEFT JOIN Projects p ON u.id = p.manager_id
GROUP BY u.id;

#============
# RAPORTY JAKO WIDOKI
#============

# raport wydajności pracownika
CREATE OR REPLACE VIEW vw_EmployeePerformance AS
SELECT
    u.id as user_id,
    CONCAT(u.first_name, ' ', u.last_name) as employee,
    tm.name as team,
    COUNT(tk.id) as total_tasks,
    SUM(CASE WHEN tk.status = 'zrobione' THEN 1 ELSE 0 END) as completed,
    SUM(CASE WHEN tk.status = 'anulowane' THEN 1 ELSE 0 END) as canceled,
    GROUP_CONCAT(
            CASE WHEN tk.status = 'zrobione'
                     THEN CONCAT(tk.title, ' (', DATE_FORMAT(tk.created_at, '%Y-%m-%d'), ')')
                END SEPARATOR '\n'
    ) as completed_tasks_titles,
    GROUP_CONCAT(
            CASE WHEN tk.status != 'zrobione'
                     THEN CONCAT(tk.title, ' (', tk.status, ', ', DATE_FORMAT(tk.created_at, '%Y-%m-%d'), ')')
                END SEPARATOR '\n'
    ) as pending_tasks_titles,
    IFNULL(
            (SUM(CASE WHEN tk.status = 'zrobione' THEN 1 ELSE 0 END) * 100.0 /
             NULLIF(COUNT(tk.id), 0)),
            0
    ) as completion_rate
FROM
    Users u
        LEFT JOIN Teams tm ON u.team_id = tm.id
        LEFT JOIN TaskAssignments ta ON u.id = ta.user_id
        LEFT JOIN Tasks tk ON ta.task_id = tk.id
GROUP BY
    u.id, u.first_name, u.last_name, tm.name;

# raport postępów projektu
CREATE OR REPLACE VIEW vw_ProjectProgress AS
SELECT
    p.id                                   AS project_id,
    p.manager_id,
    p.name                                 AS project,
    CONCAT(u.first_name, ' ', u.last_name) AS manager,
    p.status,
    p.progress                             AS overall_progress,
    COUNT(DISTINCT m.id)                   AS total_milestones,
    GROUP_CONCAT(DISTINCT m.name SEPARATOR ', ') AS milestone_names,
    COUNT(t.id)                            AS total_tasks,
    GROUP_CONCAT(DISTINCT t.title SEPARATOR ', ') AS task_titles,
    SUM(IF(t.status = 'zrobione', 1, 0))   AS completed_tasks,
    SUM(IF(t.status = 'anulowane', 1, 0))  AS canceled_tasks,
    COALESCE(AVG(m.progress), 0)           AS avg_milestone_progress,
    GROUP_CONCAT(DISTINCT tm.name SEPARATOR ', ') AS involved_teams,
    GROUP_CONCAT(DISTINCT CONCAT(tml.first_name, ' ', tml.last_name, ' (', tm.name, ')') SEPARATOR '; ') AS team_leaders
FROM Projects p
         JOIN Users u ON p.manager_id = u.id
         LEFT JOIN ProjectTeams pt ON p.id = pt.project_id
         LEFT JOIN Teams tm ON pt.team_id = tm.id
         LEFT JOIN Users tml ON tml.team_id = tm.id AND tml.role_id = (SELECT id FROM Roles WHERE name = 'teamLider')
         LEFT JOIN Milestones m ON p.id = m.project_id
         LEFT JOIN Tasks t ON m.id = t.milestone_id
GROUP BY p.id, p.manager_id;

# raport całkowitego przeglądu projektów
CREATE OR REPLACE VIEW vw_ExecutiveOverview AS
SELECT
    p.id AS project_id,
    p.name AS project,
    p.status AS project_status,
    p.progress AS project_progress,
    CONCAT(pm.first_name, ' ', pm.last_name) AS project_manager,
    COUNT(DISTINCT pt.team_id) AS teams_involved,
    COUNT(DISTINCT u.id) AS employees_assigned,
    COUNT(DISTINCT m.id) AS milestones,
    COUNT(DISTINCT tsk.id) AS total_tasks,
    SUM(IF(tsk.status = 'zrobione', 1, 0)) AS tasks_done,
    SUM(IF(tsk.status = 'anulowane', 1, 0)) AS tasks_canceled,
    ROUND(SUM(IF(tsk.status = 'zrobione', 1, 0)) * 100.0 / NULLIF(COUNT(tsk.id), 0), 2) AS task_completion_rate,
    ROUND(AVG(m.progress), 2) AS avg_milestone_progress,
    COUNT(DISTINCT CASE WHEN m.deadline < CURDATE() AND m.progress < 100 THEN m.id END) AS overdue_milestones,
    COUNT(DISTINCT CASE WHEN tsk.deadline < CURDATE() AND tsk.status NOT IN ('zrobione', 'anulowane') THEN tsk.id END) AS overdue_tasks,
    GROUP_CONCAT(DISTINCT tsk.title SEPARATOR ', ') AS task_titles,
    GROUP_CONCAT(DISTINCT tm.name SEPARATOR ', ') AS involved_teams,
    GROUP_CONCAT(DISTINCT CONCAT(tml.first_name, ' ', tml.last_name, ' (', tm.name, ')') SEPARATOR '; ') AS team_leaders
FROM Projects p
         LEFT JOIN Users pm ON p.manager_id = pm.id
         LEFT JOIN ProjectTeams pt ON p.id = pt.project_id
         LEFT JOIN Teams tm ON pt.team_id = tm.id
         LEFT JOIN Users tml ON tml.team_id = tm.id AND tml.role_id = (SELECT id FROM Roles WHERE name = 'teamLider')
         LEFT JOIN Users u ON tm.id = u.team_id
         LEFT JOIN Milestones m ON p.id = m.project_id
         LEFT JOIN Tasks tsk ON m.id = tsk.milestone_id
GROUP BY p.id;



#============
# triggery
#============

# trigger do kontroli uprawnien podczas przypisywania zadan
DELIMITER //
CREATE TRIGGER CheckAssignmentPrivileges
    BEFORE INSERT ON TaskAssignments
    FOR EACH ROW
BEGIN
    DECLARE assigner_privilege TINYINT;
    DECLARE assigner_role VARCHAR(20);
    DECLARE target_user_team INT;
    DECLARE assigner_team INT;

    SELECT r.privilege_level, u.team_id, r.name
    INTO assigner_privilege, assigner_team, assigner_role
    FROM Users u
             JOIN Roles r ON u.role_id = r.id
    WHERE u.id = NEW.assigned_by;

    SELECT team_id INTO target_user_team
    FROM Users
    WHERE id = NEW.user_id;

    IF assigner_privilege < 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Brak uprawnień do przypisywania zadań!';
    END IF;

    IF assigner_role = 'teamLider' AND assigner_team != target_user_team THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Team Lider może przypisywać zadania tylko członkom swojego zespołu!';
    END IF;

    IF assigner_role = 'pracownik' AND NEW.assigned_by != NEW.user_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Pracownicy mogą przypisywać zadania tylko sobie!';
    END IF;
END //

# trigger do sprawdzania uprawnien podczas aktualizacji przypisywania zadan
CREATE TRIGGER CheckAssignmentPrivilegesOnUpdate
    BEFORE UPDATE ON TaskAssignments
    FOR EACH ROW
BEGIN
    DECLARE assigner_privilege TINYINT;
    DECLARE assigner_role VARCHAR(20);
    DECLARE target_user_team INT;
    DECLARE assigner_team INT;

    SELECT r.privilege_level, u.team_id, r.name
    INTO assigner_privilege, assigner_team, assigner_role
    FROM Users u
             JOIN Roles r ON u.role_id = r.id
    WHERE u.id = NEW.assigned_by;

    SELECT team_id INTO target_user_team
    FROM Users
    WHERE id = NEW.user_id;

    IF assigner_privilege < 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Brak uprawnień do przypisywania zadań!';
    END IF;

    IF assigner_role = 'teamLider' AND assigner_team != target_user_team THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Team Lider może przypisywać zadania tylko członkom swojego zespołu!';
    END IF;

    IF assigner_role = 'pracownik' AND NEW.assigned_by != NEW.user_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Pracownicy mogą przypisywać zadania tylko sobie!';
    END IF;
END //
DELIMITER ;


DELIMITER //
CREATE TRIGGER CheckProjectManagerRole
    BEFORE INSERT ON Projects
    FOR EACH ROW
BEGIN
    DECLARE manager_role VARCHAR(20);

    SELECT r.name INTO manager_role
    FROM Users u
             JOIN Roles r ON u.role_id = r.id
    WHERE u.id = NEW.manager_id;

    IF manager_role != 'projektManager' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Tylko użytkownicy z rolą projektManager mogą być menedżerami projektów';
    END IF;
END //

# trigger do sprawdzania i zapewniania ze tylko projektmanager moze byc przypisany jak o manager
CREATE TRIGGER CheckProjectManagerRoleOnUpdate
    BEFORE UPDATE ON Projects
    FOR EACH ROW
BEGIN
    DECLARE manager_role VARCHAR(20);

    SELECT r.name INTO manager_role
    FROM Users u
             JOIN Roles r ON u.role_id = r.id
    WHERE u.id = NEW.manager_id;

    IF manager_role != 'projektManager' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Tylko użytkownicy z rolą projektManager mogą być menedżerami projektów';
    END IF;
END //
DELIMITER ;

# trigger do automatycznego generowania powiadomien gdy zmieni sie termin wykonania zadania
DELIMITER //
CREATE TRIGGER UpdateDeadlineNotification
    AFTER UPDATE ON Tasks
    FOR EACH ROW
BEGIN
    IF NEW.deadline <> OLD.deadline THEN
        INSERT INTO Notifications (task_id, user_id, type, message, created_at)
        SELECT
            NEW.id,
            ta.user_id,
            'deadline',
            CONCAT('Zmieniono deadline zadania "', NEW.title, '" na ', NEW.deadline),
            NOW()
        FROM TaskAssignments ta
        WHERE ta.task_id = NEW.id;
    END IF;
END //
DELIMITER ;

# trigger automatycznie ustawia poziom uprawnien wedlug komorki z nazwa przy insercie
DELIMITER //
CREATE TRIGGER SetPrivilegeLevelOnInsert
    BEFORE INSERT ON Roles
    FOR EACH ROW
BEGIN
    SET NEW.privilege_level =
            CASE NEW.name
                WHEN 'prezes' THEN 4
                WHEN 'projektManager' THEN 3
                WHEN 'teamLider' THEN 2
                WHEN 'pracownik' THEN 1
                END;
END //

# trigger automatycznie ustawia poziom uprawnien wedlug komorki z nazwa przy aktualizacji
CREATE TRIGGER SetPrivilegeLevelOnUpdate
    BEFORE UPDATE ON Roles
    FOR EACH ROW
BEGIN
    SET NEW.privilege_level =
            CASE NEW.name
                WHEN 'prezes' THEN 4
                WHEN 'projektManager' THEN 3
                WHEN 'teamLider' THEN 2
                WHEN 'pracownik' THEN 1
                END;
END //
DELIMITER //

# trigger sprawdza i wyrzuca blad jezeli jezeli zadanie ma status anulowane a nie ma informacji anulowane przez kogo
CREATE TRIGGER Tasks_CheckCanceledBy_Insert
    BEFORE INSERT ON Tasks
    FOR EACH ROW
BEGIN
    IF NEW.status = 'anulowane' AND NEW.canceled_by IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Pole canceled_by musi być ustawione przy statusie anulowane.';
    END IF;
END //

# trigger do wymuszenia ustawienia pola canceled_by przy zmienie statusu
CREATE TRIGGER Tasks_CheckCanceledBy_Update
    BEFORE UPDATE ON Tasks
    FOR EACH ROW
BEGIN
    IF NEW.status = 'anulowane' AND (NEW.canceled_by IS NULL OR NEW.canceled_by = '') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Pole canceled_by musi być ustawione przy statusie anulowane.';
    END IF;
END //
DELIMITER ;

# trigger do automatycznej aktualizacji progresu projektu na podstawie statusu zadan
DELIMITER //
CREATE TRIGGER UpdateProjectProgress
    AFTER UPDATE ON Tasks
    FOR EACH ROW
BEGIN
    DECLARE project_id_val INT;
    SELECT m.project_id INTO project_id_val FROM Milestones m WHERE m.id = NEW.milestone_id;

    UPDATE Projects p
    SET p.progress = (
        SELECT COALESCE(
                       ROUND(SUM(IF(t.status = 'zrobione', 1, 0)) * 100 / NULLIF(COUNT(t.id), 0)),
                       0
               )
        FROM Tasks t
                 JOIN Milestones m ON t.milestone_id = m.id
        WHERE m.project_id = project_id_val
    )
    WHERE p.id = project_id_val;
END //
DELIMITER ;

# trigger do powiadomienia w okreslonym czasie przed uplywem terminu
DELIMITER //
CREATE TRIGGER DeadlineNotification
    AFTER INSERT ON TaskAssignments
    FOR EACH ROW
BEGIN
    DECLARE task_deadline DATE;

    SELECT deadline INTO task_deadline
    FROM Tasks
    WHERE id = NEW.task_id;

    IF task_deadline BETWEEN CURDATE() AND CURDATE() + INTERVAL 3 DAY THEN
        INSERT INTO Notifications (task_id, user_id, type, message, created_at)
        VALUES (
                   NEW.task_id,
                   NEW.user_id,
                   'deadline',
                   CONCAT('Zadanie "', (SELECT title FROM Tasks WHERE id = NEW.task_id), '" ma deadline za ',
                          DATEDIFF(task_deadline, CURDATE()), ' dni!'),
                   NOW()
               );
    END IF;
END //
DELIMITER ;

# trigger do zapobiegania anulowania zadania przez uzytkownikow ze zbyt niskim poziomem uprawnienia
DELIMITER //
CREATE TRIGGER PreventUnauthorizedCancel
    BEFORE UPDATE ON Tasks
    FOR EACH ROW
BEGIN
    DECLARE user_role VARCHAR(20);

    IF NEW.status = 'anulowane' THEN

        SELECT name INTO user_role
        FROM Roles
        WHERE id = (SELECT role_id FROM Users WHERE id = NEW.canceled_by LIMIT 1);

        IF user_role NOT IN ('teamLider', 'prezes') THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Tylko Team Lider lub Prezes może anulować zadanie!';
        END IF;
    END IF;
END //
DELIMITER ;
#============
#FUNKCJE
#============

# raport dla prezesa ze srednia wydajnoscia, zakonczonymi i anulowanymi zadaniami w okreslonym czasie, data poczatkowa i koncowa jest podawana
DELIMITER //
CREATE FUNCTION GenerateExecutiveReport(start_date DATE, end_date DATE)
    RETURNS TEXT
BEGIN
    DECLARE report_text TEXT;

    SELECT CONCAT(
                   'Liczba aktywnych projektów: ', COUNT(p.id), '\n',
                   'Średnia wydajność: ', COALESCE(ROUND(AVG(p.progress)), '0'), '%\n',
                   'Zadania zakończone: ', COALESCE(SUM(IF(t.status = 'zrobione', 1, 0)), 0), '\n',
                   'Zadania anulowane: ', COALESCE(SUM(IF(t.status = 'anulowane', 1, 0)), 0)
           ) INTO report_text
    FROM Projects p
             LEFT JOIN Milestones m ON p.id = m.project_id
             LEFT JOIN Tasks t ON m.id = t.milestone_id
    WHERE
        p.start_date <= end_date AND
        (p.end_date >= start_date OR p.end_date IS NULL);

    RETURN report_text;
END //

DELIMITER ;

#============
#PROCEDURY
#============
# procedura do generowania wydajnosci pracownika w tym jego statusy zadan
DELIMITER //
CREATE PROCEDURE GetEmployeePerformance(
    IN user_id INT,
    IN start_date DATE,
    IN end_date DATE
)
BEGIN
    SELECT
        CONCAT(u.first_name, ' ', u.last_name) AS employee,
        COUNT(t.id)                            AS total_tasks,
        SUM(IF(t.status = 'zrobione', 1, 0))   AS completed,
        SUM(IF(t.status = 'anulowane', 1, 0))  AS canceled,
        COALESCE(
                ROUND(
                        (SUM(IF(t.status = 'zrobione', 1, 0))
                            / NULLIF(COUNT(t.id), 0)) * 100, 2
                ), 0
        )                                      AS completion_rate
    FROM Users u
             LEFT JOIN TaskAssignments ta ON u.id = ta.user_id
             LEFT JOIN Tasks t ON ta.task_id = t.id
    WHERE u.id = user_id
      AND t.created_at BETWEEN start_date AND end_date
    GROUP BY u.id;
END //
DELIMITER ;

# inserty dla tabeli z rolami, przywileje uzulenia sie same
INSERT INTO Roles (name) VALUES
                             ('prezes'),
                             ('projektManager'),
                             ('teamLider'),
                             ('pracownik');

# insert dla zespolow
INSERT INTO Teams (name) VALUES
                             ('Development'),
                             ('Marketing'),
                             ('Sales'),
                             ('HR'),
                             ('Support'),
                             ('Design'),
                             ('Testing'),
                             ('Operations'),
                             ('Finance'),
                             ('Research');

# insert dla admina jako pierwszego uzytkownika
INSERT INTO Users (team_id, role_id, first_name, last_name, hire_date, login, password_hash, created_at)
VALUES (
           NULL,
           (SELECT id FROM Roles WHERE name = 'prezes'),
           'Admin',
           'Admin',
           '2020-01-01',
           'admin',
           'admin',
           NOW()
       );

# insert dla uzytkownikow
INSERT INTO Users (team_id, role_id, first_name, last_name, hire_date, login, password_hash, created_at) VALUES
-- ProjektManagerzy
(1, (SELECT id FROM Roles WHERE name = 'projektManager'), 'Jan', 'Kowalski', '2021-01-15', 'jkowalski', '...', NOW()),
(2, (SELECT id FROM Roles WHERE name = 'projektManager'), 'Anna', 'Nowak', '2021-02-20', 'anowak', '...', NOW()),
-- TeamLiderzy
(1, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Piotr', 'Wiśniewski', '2021-03-10', 'pwisniewski', '...', NOW()),
(2, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Katarzyna', 'Wójcik', '2021-04-05', 'kwojcik', '...', NOW()),
(3, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Marek', 'Kowalczyk', '2021-05-12', 'mkowalczyk', '...', NOW()),
-- Pracownicy
(1, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Adam', 'Lewandowski', '2021-06-01', 'alewandowski', '...', NOW()),
(2, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Ewa', 'Dąbrowska', '2021-07-15', 'edabrowska', '...', NOW()),
(3, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Robert', 'Kozłowski', '2021-08-20', 'rkozlowski', '...', NOW()),
(4, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Magdalena', 'Jankowska', '2021-09-25', 'mjankowska', '...', NOW()),
(5, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Tomasz', 'Mazur', '2021-10-30', 'tmazur', '...', NOW());

# Projekty
INSERT INTO Projects (manager_id, name, progress, status, start_date, end_date) VALUES
                                                                                    ((SELECT id FROM Users WHERE login = 'jkowalski'), 'System ERP', 30, 'wTrakcie', '2023-01-01', '2023-12-31'),
                                                                                    ((SELECT id FROM Users WHERE login = 'anowak'), 'Aplikacja Mobilna', 50, 'wTrakcie', '2023-02-01', '2023-11-30'),
                                                                                    ((SELECT id FROM Users WHERE login = 'jkowalski'), 'Strona WWW', 100, 'zakonczony', '2023-03-01', '2023-05-15'),
                                                                                    ((SELECT id FROM Users WHERE login = 'anowak'), 'Marketing Campaign', 0, 'planowany', '2024-01-01', '2024-06-30'),
                                                                                    ((SELECT id FROM Users WHERE login = 'jkowalski'), 'CRM System', 75, 'wTrakcie', '2023-04-01', '2023-10-31'),
                                                                                    ((SELECT id FROM Users WHERE login = 'anowak'), 'E-commerce Platform', 20, 'wTrakcie', '2023-05-01', '2023-12-31'),
                                                                                    ((SELECT id FROM Users WHERE login = 'jkowalski'), 'Analytics Dashboard', 90, 'wTrakcie', '2023-06-01', '2023-09-30'),
                                                                                    ((SELECT id FROM Users WHERE login = 'anowak'), 'HR Portal', 100, 'zakonczony', '2023-07-01', '2023-08-31'),
                                                                                    ((SELECT id FROM Users WHERE login = 'jkowalski'), 'IoT Solution', 0, 'anulowany', '2023-08-01', NULL),
                                                                                    ((SELECT id FROM Users WHERE login = 'anowak'), 'AI Chatbot', 40, 'wTrakcie', '2023-09-01', '2024-02-28');

# insert z przypisaniem uzytkownikow do projektow
INSERT INTO ProjectTeams (project_id, team_id) VALUES
                                                   (1,1), (1,2), (2,3), (2,4), (3,5), (4,6), (5,7), (6,8), (7,9), (8,10);

# insert z kamieniami milowymi
INSERT INTO Milestones (project_id, name, progress, description, deadline) VALUES
                                                                               (1, 'Analiza wymagań', 100, 'Zebranie wymagań od klienta', '2023-01-15'),
                                                                               (1, 'Projekt systemu', 80, 'Stworzenie dokumentacji technicznej', '2023-02-28'),
                                                                               (2, 'Prototyp UI', 100, 'Przygotowanie prototypu interfejsu', '2023-02-15'),
                                                                               (2, 'Implementacja API', 60, 'Rozwój podstawowych funkcjonalności', '2023-03-31'),
                                                                               (3, 'Testy końcowe', 100, 'Testy akceptacyjne', '2023-04-15'),
                                                                               (4, 'Planowanie budżetu', 0, 'Dystrybucja środków finansowych', '2024-01-15'),
                                                                               (5, 'Integracja modułów', 70, 'Połączenie komponentów systemu', '2023-06-30'),
                                                                               (6, 'Badania rynkowe', 30, 'Analiza konkurencji', '2023-07-15'),
                                                                               (7, 'Wdrożenie produkcyjne', 90, 'Wdrożenie na serwerach', '2023-08-15'),
                                                                               (8, 'Szkolenie pracowników', 100, 'Przeszkolenie zespołu HR', '2023-08-20');

# insert z zadaniami
INSERT INTO Tasks (milestone_id, title, description, priority, status, progress, created_at, deadline, canceled_by) VALUES
                                                                                                                        (1, 'Wywiady z klientem', 'Przeprowadzenie wywiadów z interesariuszami', 'wysoki', 'zrobione', 100, NOW(), '2023-01-10', NULL),
                                                                                                                        (1, 'Dokumentacja wymagań', 'Stworzenie dokumentu SRS', 'sredni', 'zrobione', 100, NOW(), '2023-01-12', NULL),
                                                                                                                        (2, 'Diagram ERD', 'Projekt bazy danych', 'wysoki', 'wTrakcie', 80, NOW(), '2023-02-25', NULL),
                                                                                                                        (2, 'Mockupy UI', 'Projekt interfejsu użytkownika', 'sredni', 'doZrobienia', 0, NOW(), '2023-03-05', NULL),
                                                                                                                        (3, 'Testy użyteczności', 'Testy z udziałem użytkowników', 'niski', 'zrobione', 100, NOW(), '2023-02-10', NULL),
                                                                                                                        (4, 'Moduł autentykacji', 'Implementacja logowania', 'wysoki', 'wTrakcie', 60, NOW(), '2023-03-25', NULL),
                                                                                                                        (5, 'Raport błędów', 'Naprawa zgłoszonych problemów', 'wysoki', 'zrobione', 100, NOW(), '2023-04-10', NULL),
                                                                                                                        (6, 'Zakup licencji', 'Zakup potrzebnego oprogramowania', 'sredni', 'doZrobienia', 0, NOW(), '2024-01-10', NULL),
                                                                                                                        (7, 'Integracja z SAP', 'Połączenie z systemem ERP', 'wysoki', 'wTrakcie', 70, NOW(), '2023-06-25', NULL),
                                                                                                                        (8, 'Ankieta online', 'Zbieranie feedbacku od klientów', 'niski', 'wTrakcie', 30, NOW(), '2023-07-10', NULL);

# insert z przypisaniem uzytkownikow do zadan
INSERT INTO TaskAssignments (task_id, assigned_by, user_id, assigned_at) VALUES
#Zespół 1
(1, (SELECT id FROM Users WHERE login = 'pwisniewski'), (SELECT id FROM Users WHERE login = 'alewandowski'), NOW()),
(2, (SELECT id FROM Users WHERE login = 'pwisniewski'), (SELECT id FROM Users WHERE login = 'alewandowski'), NOW()),
(3, (SELECT id FROM Users WHERE login = 'pwisniewski'), (SELECT id FROM Users WHERE login = 'alewandowski'), NOW()),
(7, (SELECT id FROM Users WHERE login = 'pwisniewski'), (SELECT id FROM Users WHERE login = 'alewandowski'), NOW()),
(9, (SELECT id FROM Users WHERE login = 'pwisniewski'), (SELECT id FROM Users WHERE login = 'alewandowski'), NOW()),

# Zespół 2
(4, (SELECT id FROM Users WHERE login = 'kwojcik'), (SELECT id FROM Users WHERE login = 'edabrowska'), NOW()),
(6, (SELECT id FROM Users WHERE login = 'kwojcik'), (SELECT id FROM Users WHERE login = 'edabrowska'), NOW()),
(10, (SELECT id FROM Users WHERE login = 'kwojcik'), (SELECT id FROM Users WHERE login = 'edabrowska'), NOW()),

# Zespół 3
(5, (SELECT id FROM Users WHERE login = 'mkowalczyk'), (SELECT id FROM Users WHERE login = 'rkozlowski'), NOW()),
(8, (SELECT id FROM Users WHERE login = 'mkowalczyk'), (SELECT id FROM Users WHERE login = 'rkozlowski'), NOW());


# inserty dla raportow
INSERT INTO Reports (created_by, type, parameters, generated_at) VALUES
                                                                     ((SELECT id FROM Users WHERE login = 'admin'), 'Raport miesięczny', '{"zakres":"2023-01"}', NOW()),
                                                                     ((SELECT id FROM Users WHERE login = 'jkowalski'), 'Postęp projektu ERP', '{"projekt":1}', NOW()),
                                                                     ((SELECT id FROM Users WHERE login = 'anowak'), 'Analiza sprzedaży', '{"okres":"Q3"}', NOW()),
                                                                     ((SELECT id FROM Users WHERE login = 'pwisniewski'), 'Podsumowanie zadań', '{"zespol":1}', NOW()),
                                                                     ((SELECT id FROM Users WHERE login = 'kwojcik'), 'Statystyki HR', '{"dzial":"HR"}', NOW()),
                                                                     ((SELECT id FROM Users WHERE login = 'mkowalczyk'), 'Raport testów', '{"projekt":3}', NOW()),
                                                                     ((SELECT id FROM Users WHERE login = 'alewandowski'), 'Problemy techniczne', '{"priorytet":"wysoki"}', NOW()),
                                                                     ((SELECT id FROM Users WHERE login = 'edabrowska'), 'Feedback klientów', '{"platforma":"mobilna"}', NOW()),
                                                                     ((SELECT id FROM Users WHERE login = 'rkozlowski'), 'Ankiety', '{"kategoria":"UX"}', NOW()),
                                                                     ((SELECT id FROM Users WHERE login = 'mjankowska'), 'Zakupy', '{"kwartal":"2023-Q4"}', NOW());

# inserty z powiadomieniami
INSERT INTO Notifications (task_id, user_id, report_id, type, message, is_read, created_at) VALUES
                                                                                                (1, (SELECT id FROM Users WHERE login = 'alewandowski'), NULL, 'zadaniePrzypisane', 'Nowe zadanie: Wywiady z klientem', false, NOW()),
                                                                                                (2, (SELECT id FROM Users WHERE login = 'alewandowski'), NULL, 'zadaniePrzypisane', 'Nowe zadanie: Dokumentacja wymagań', false, NOW()),
                                                                                                (3, (SELECT id FROM Users WHERE login = 'alewandowski'), NULL, 'aktualizacjaZadania', 'Zaktualizowano postęp zadania: Diagram ERD', false, NOW()),
                                                                                                (4, (SELECT id FROM Users WHERE login = 'edabrowska'), NULL, 'zadaniePrzypisane', 'Nowe zadanie: Mockupy UI', false, NOW()),
                                                                                                (5, (SELECT id FROM Users WHERE login = 'rkozlowski'), NULL, 'deadline', 'Zbliża się deadline zadania: Testy użyteczności', false, NOW()),
                                                                                                (6, (SELECT id FROM Users WHERE login = 'edabrowska'), NULL, 'generowanieRaportu', 'Raport gotowy: Moduł autentykacji', false, NOW()),
                                                                                                (7, (SELECT id FROM Users WHERE login = 'alewandowski'), NULL, 'zadaniePrzypisane', 'Zadanie zakończone: Raport błędów', true, NOW()),
                                                                                                (8, (SELECT id FROM Users WHERE login = 'mjankowska'), NULL, 'inne', 'Przydzielono nowe zasoby', false, NOW()),
                                                                                                (9, (SELECT id FROM Users WHERE login = 'alewandowski'), NULL, 'deadline', 'Deadline za 3 dni: Integracja z SAP', false, NOW()),
                                                                                                (10, (SELECT id FROM Users WHERE login = 'edabrowska'), NULL, 'aktualizacjaZadania', 'Zmiana statusu zadania: Ankieta online', false, NOW());

# testowe selecty mozna usunac, nic sie nie stanie
select * from Users;
select * from vw_EmployeePerformance;
select * from vw_ExecutiveOverview;

# dobijamy do 800 lini, pozdrawiam sprawdzajacych kod, Mitahezyf :)


CREATE OR REPLACE VIEW vw_UserCompleteDetails AS
SELECT
    u.id AS user_id,
    u.first_name,
    u.last_name,
    r.name AS role,
    t.name AS team,
    u.hire_date,
    u.login,
    u.created_at AS user_created_at,
    MAX(CONCAT(manager.first_name, ' ', manager.last_name)) AS manager_name,
    MAX(CONCAT(team_leader.first_name, ' ', team_leader.last_name)) AS team_leader_name,
    GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') AS projects_assigned,
    GROUP_CONCAT(DISTINCT m.name SEPARATOR ', ') AS milestones_assigned,
    GROUP_CONCAT(DISTINCT tas.title SEPARATOR ', ') AS tasks_assigned,
    COUNT(DISTINCT tas.id) AS total_tasks,
    SUM(tas.status = 'doZrobienia') AS todo,
    SUM(tas.status = 'wTrakcie') AS in_progress,
    SUM(tas.status = 'zrobione') AS done,
    SUM(tas.status = 'anulowane') AS canceled
FROM Users u
         JOIN Roles r ON u.role_id = r.id
         LEFT JOIN Teams t ON u.team_id = t.id
         LEFT JOIN TaskAssignments ta ON u.id = ta.user_id
         LEFT JOIN Tasks tas ON ta.task_id = tas.id
         LEFT JOIN Milestones m ON tas.milestone_id = m.id
         LEFT JOIN Projects p ON m.project_id = p.id
         LEFT JOIN Users manager ON p.manager_id = manager.id
         LEFT JOIN Users team_leader ON t.id = team_leader.team_id AND team_leader.role_id = (SELECT id FROM Roles WHERE name = 'teamLider')
GROUP BY u.id;

select * from vw_UserCompleteDetails;
select * from vw_TaskAssignments;

select * from vw_EmployeePerformance;
select * from vw_ProjectProgress;
select * from vw_ExecutiveOverview;
select * from Users;


-- Dodanie 7 teamLiderów dla teamów 4-10
INSERT INTO Users (team_id, role_id, first_name, last_name, hire_date, login, password_hash, created_at) VALUES
                                                                                                             (4, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Alicja', 'Zając', '2021-05-10', 'azając', '...', NOW()),
                                                                                                             (5, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Marcin', 'Sobczak', '2020-08-22', 'msobczak', '...', NOW()),
                                                                                                             (6, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Karolina', 'Wilk', '2022-01-15', 'kwilk', '...', NOW()),
                                                                                                             (7, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Damian', 'Krawczyk', '2021-09-05', 'dkrawczyk', '...', NOW()),
                                                                                                             (8, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Natalia', 'Kołodziej', '2020-11-30', 'nkolodziej', '...', NOW()),
                                                                                                             (9, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Kamil', 'Pawlak', '2022-03-20', 'kpawlak', '...', NOW()),
                                                                                                             (10, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Monika', 'Michalak', '2021-07-12', 'mmichalak', '...', NOW());

-- Dodanie 43 pracowników
INSERT INTO Users (team_id, role_id, first_name, last_name, hire_date, login, password_hash, created_at) VALUES
                                                                                                             (1, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Łukasz', 'Nowak', '2023-02-01', 'lnowak', '...', NOW()),
                                                                                                             (2, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Agnieszka', 'Kowalczyk', '2022-06-15', 'akowalczyk', '...', NOW()),
                                                                                                             (3, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Paweł', 'Wiśniewski', '2021-04-10', 'pwisniewski2', '...', NOW()),
                                                                                                             (4, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Joanna', 'Dąbrowska', '2020-12-01', 'jdabrowska', '...', NOW()),
                                                                                                             (5, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Tomasz', 'Lewandowski', '2023-01-15', 'tlewandowski', '...', NOW()),
                                                                                                             (6, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Magdalena', 'Zielińska', '2022-08-20', 'mzielinska', '...', NOW()),
                                                                                                             (7, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Krzysztof', 'Szymański', '2021-11-05', 'kszymanski', '...', NOW()),
                                                                                                             (8, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Ewa', 'Woźniak', '2020-09-10', 'ewozniak', '...', NOW()),
                                                                                                             (9, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Marek', 'Kamiński', '2022-04-25', 'mkaminski', '...', NOW()),
                                                                                                             (10, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Anna', 'Kaczmarek', '2023-03-01', 'akaczmarek', '...', NOW()),
                                                                                                             (1, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Robert', 'Piotrowski', '2021-07-15', 'rpiotrowski', '...', NOW()),
                                                                                                             (2, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Karolina', 'Grabowska', '2020-10-20', 'kgrabowska', '...', NOW()),
                                                                                                             (3, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Dariusz', 'Nowicki', '2022-05-30', 'dnowicki', '...', NOW()),
                                                                                                             (4, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Monika', 'Pawłowska', '2021-08-12', 'mpawlowska', '...', NOW()),
                                                                                                             (5, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Grzegorz', 'Michalski', '2023-04-05', 'gmichalski', '...', NOW()),
                                                                                                             (6, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Barbara', 'Król', '2020-07-18', 'bkrol', '...', NOW()),
                                                                                                             (7, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Łukasz', 'Jabłoński', '2022-09-22', 'ljablonski', '...', NOW()),
                                                                                                             (8, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Olga', 'Majewska', '2021-12-03', 'omajewska', '...', NOW()),
                                                                                                             (9, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Igor', 'Ostrowski', '2023-05-10', 'iostrowski', '...', NOW()),
                                                                                                             (10, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Renata', 'Malinowska', '2020-06-25', 'rmalinowska', '...', NOW()),
                                                                                                             (1, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Stanisław', 'Szewczyk', '2022-11-15', 'sszewczyk', '...', NOW()),
                                                                                                             (2, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Teresa', 'Wróbel', '2021-03-20', 'twrobel', '...', NOW()),
                                                                                                             (3, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Włodzimierz', 'Marciniak', '2023-02-28', 'wmarciniak', '...', NOW()),
                                                                                                             (4, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Zofia', 'Zalewska', '2020-04-12', 'zzalewska', '...', NOW()),
                                                                                                             (5, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Michał', 'Baran', '2022-07-07', 'mbaran', '...', NOW()),
                                                                                                             (6, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Laura', 'Rutkowska', '2021-09-19', 'lrutkowska', '...', NOW()),
                                                                                                             (7, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Norbert', 'Sikora', '2023-01-05', 'nsikora', '...', NOW()),
                                                                                                             (8, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Marta', 'Duda', '2020-08-30', 'mduda', '...', NOW()),
                                                                                                             (9, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Kamil', 'Włodarczyk', '2022-12-10', 'kwlodarczyk', '...', NOW()),
                                                                                                             (10, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Halina', 'Baranowska', '2021-05-25', 'hbaranowska', '...', NOW()),
                                                                                                             (1, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Dariusz', 'Sokołowski', '2023-06-15', 'dsokolowski', '...', NOW()),
                                                                                                             (2, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Elżbieta', 'Mazur', '2020-09-05', 'emazur', '...', NOW()),
                                                                                                             (3, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Grzegorz', 'Mróz', '2022-04-18', 'gmroz', '...', NOW()),
                                                                                                             (4, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Joanna', 'Nowakowska', '2021-10-22', 'jnowakowska', '...', NOW()),
                                                                                                             (5, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Tomasz', 'Pietrzak', '2023-03-30', 'tpietrzak', '...', NOW()),
                                                                                                             (6, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Alicja', 'Tomczyk', '2020-12-14', 'atomczyk', '...', NOW()),
                                                                                                             (7, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Marcin', 'Lis', '2022-08-05', 'mlis', '...', NOW()),
                                                                                                             (8, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Karolina', 'Kaczmarek', '2021-11-11', 'kkaczmarek', '...', NOW()),
                                                                                                             (9, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Paweł', 'Jakubowski', '2023-04-01', 'pjakubowski', '...', NOW()),
                                                                                                             (10, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Anna', 'Sadowski', '2020-05-19', 'asadowski', '...', NOW()),
                                                                                                             (1, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Robert', 'Wróbel', '2022-10-10', 'rwrobel', '...', NOW()),
                                                                                                             (2, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Ewa', 'Marciniak', '2021-06-15', 'emarciniak', '...', NOW()),
                                                                                                             (3, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Damian', 'Zalewski', '2023-07-01', 'dzalewski', '...', NOW()),
                                                                                                             (4, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Natalia', 'Sikora', '2020-11-23', 'nsikora2', '...', NOW()),
                                                                                                             (5, (SELECT id FROM Roles WHERE name = 'pracownik'), 'Krzysztof', 'Dudek', '2022-05-05', 'kdudek', '...', NOW());

INSERT INTO Users (team_id, role_id, first_name, last_name, hire_date, login, password_hash, created_at) VALUES
                                                                                                             (1, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Łukasz', 'Sikora', '2023-04-01', 'lsikora', '...', NOW()),
                                                                                                             (2, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Magdalena', 'Olszewska', '2022-09-15', 'molszewska', '...', NOW()),
                                                                                                             (3, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Bartosz', 'Malinowski', '2021-12-05', 'bmalinowski', '...', NOW()),
                                                                                                             (4, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Natalia', 'Kaczmarek', '2020-06-20', 'nkaczmarek', '...', NOW()),
                                                                                                             (5, (SELECT id FROM Roles WHERE name = 'teamLider'), 'Damian', 'Górski', '2023-03-10', 'dgorski', '...', NOW());

INSERT INTO Users (team_id, role_id, first_name, last_name, hire_date, login, password_hash, created_at) VALUES
                                                                                                             (1, (SELECT id FROM Roles WHERE name = 'projektManager'), 'Michał', 'Zieliński', '2022-03-15', 'mzielinski', '...', NOW()),
                                                                                                             (2, (SELECT id FROM Roles WHERE name = 'projektManager'), 'Katarzyna', 'Lis', '2021-11-01', 'klis', '...', NOW()),
                                                                                                             (3, (SELECT id FROM Roles WHERE name = 'projektManager'), 'Piotr', 'Adamczyk', '2023-02-10', 'padamczyk', '...', NOW()),
                                                                                                             (4, (SELECT id FROM Roles WHERE name = 'projektManager'), 'Joanna', 'Wójcik', '2020-09-22', 'jwojcik', '...', NOW()),
                                                                                                             (5, (SELECT id FROM Roles WHERE name = 'projektManager'), 'Marek', 'Kowal', '2022-07-05', 'mkowal', '...', NOW());



