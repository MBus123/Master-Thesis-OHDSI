CREATE TABLE ${ohdsiSchema}.custom_machine_learning_algorithm (
	id BIGINT  NOT NULL PRIMARY KEY,
	name VARCHAR(50),
	description VARCHAR(255),
	code VARCHAR,
	hyper_parameters VARCHAR,
	created_by_id INT,
	created_date TIMESTAMP,
	modified_by_id INT,
	modified_date TIMESTAMP
);

CREATE TABLE ${ohdsiSchema}.new_algorithm_queue (
	id BIGINT  NOT NULL PRIMARY KEY,
	secret_number INT,
	model_name VARCHAR,
	created_by_id INT,
	created_date TIMESTAMP,
	modified_by_id INT,
	modified_date TIMESTAMP,
	PRIMARY KEY (id)	
)
