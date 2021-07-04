package org.ohdsi.webapi.algorithm;

import java.io.File;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.ohdsi.webapi.model.CommonEntity;


@Entity(name = "Certificate")
@Table(name = "new_algorithm_queue")
public class Certificate extends CommonEntity {

    public Certificate() {

    }

    public Certificate(int secret, String name) {
        if (name == null) {
            throw new NullPointerException();
        }
        if (secret < 0 || name.isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.secret = secret;
        this.name = name;
    }

    @Id
    @GenericGenerator(
        name = "cert_generator",
        strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
        parameters = {
            @Parameter(name = "sequence_name", value = "cert_seq"),
            @Parameter(name = "increment_size", value = "1")
        }
    )
    @GeneratedValue(generator = "cert_generator")
    @Column(name = "id")
    private Integer id;

    @Column(name = "model_name")
    private String name;

    @Column(name = "secret_number")
    private int secret;


    public void setSecret(int secret) {
        this.secret = secret;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public int getSecret() {
        return this.secret;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public Integer getId() {
        return this.id;
    }
    
}
