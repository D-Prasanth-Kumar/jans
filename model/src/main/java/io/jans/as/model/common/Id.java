/*
 * Janssen Project software is available under the Apache License (2004). See http://www.apache.org/licenses/ for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.as.model.common;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.jboss.resteasy.annotations.providers.jaxb.IgnoreMediaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 26/06/2013
 */
@IgnoreMediaTypes("application/*+json")
@XmlRootElement
public class Id implements Serializable {

    private String id;

    public Id() {
    }

    public Id(String p_id) {
        id = p_id;
    }

    @JsonProperty(value = "id")
    @XmlElement(name = "id")
    public String getId() {
        return id;
    }

    public void setId(String p_id) {
        id = p_id;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Id");
        sb.append("{id='").append(id).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
