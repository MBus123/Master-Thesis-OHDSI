package org.ohdsi.webapi.algorithm;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.ohdsi.webapi.model.CommonEntity;


@Entity(name = "CustomAlgorithm")
@Table(name = "custom_machine_learning_algorithm")
public class CustomAlgorithm extends CommonEntity {

    @Id
    @GenericGenerator(
        name = "custom_generator",
        strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
        parameters = {
            @Parameter(name = "sequence_name", value = "custom_seq"),
            @Parameter(name = "increment_size", value = "1")
        }
    )
    @GeneratedValue(generator = "custom_generator")
    @Column(name = "id")
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "code")
    private String code;

    @Column(name = "hyper_parameters")
    private String hyperParameters;

    @Column(name = "file")
    private String file;

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }

    public String getHyperParameters() {
        return hyperParameters;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFile() {
        return this.file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public void setHyperParameters(String hyperParameters) {
        this.hyperParameters = hyperParameters;
    }

    public void setName(String name) {
        this.name = name;
    }
}