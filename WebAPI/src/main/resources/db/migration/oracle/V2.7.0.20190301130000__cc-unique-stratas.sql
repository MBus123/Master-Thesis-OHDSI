ALTER TABLE ${ohdsiSchema}.cc_strata ADD CONSTRAINT cc_strata_name_uq UNIQUE (cohort_characterization_id, name);