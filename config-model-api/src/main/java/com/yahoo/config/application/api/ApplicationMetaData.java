// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.*;

import com.yahoo.text.Utf8;

import java.io.*;

/**
 * Metadata about an application package.
 *
 * @author hmusum
 */
public class ApplicationMetaData {

    private final String deployedByUser;
    private final String deployedFromDir;
    private final long deployTimestamp;
    private final boolean internalRedeploy;
    private final ApplicationId applicationId;
    private final String checksum;
    private final long generation;
    private final long previousActiveGeneration;

    // TODO: Remove after September 2019
    public ApplicationMetaData(File appDir,
                               String deployedByUser,
                               String deployedFromDir,
                               Long deployTimestamp,
                               boolean internalRedeploy,
                               String checksum,
                               Long generation,
                               long previousActiveGeneration) {
        this(deployedByUser, deployedFromDir, deployTimestamp, internalRedeploy,
             appDir.getName(), checksum, generation, previousActiveGeneration);
    }

    // TODO: Remove after September 2019
    public ApplicationMetaData(String deployedByUser, String deployedFromDir, Long deployTimestamp, boolean internalRedeploy,
                               String applicationName, String checksum, Long generation, long previousActiveGeneration) {
        this(deployedByUser,
             deployedFromDir,
             deployTimestamp,
             internalRedeploy,
             ApplicationId.from(TenantName.defaultName(), ApplicationName.from(applicationName), InstanceName.from("default")),
             checksum,
             generation,
             previousActiveGeneration);
    }

    public ApplicationMetaData(String deployedByUser, String deployedFromDir, Long deployTimestamp, boolean internalRedeploy,
                               ApplicationId applicationId, String checksum, Long generation, long previousActiveGeneration) {
        this.deployedByUser = deployedByUser;
        this.deployedFromDir = deployedFromDir;
        this.deployTimestamp = deployTimestamp;
        this.internalRedeploy = internalRedeploy;
        this.applicationId = applicationId;
        this.checksum = checksum;
        this.generation = generation;
        this.previousActiveGeneration = previousActiveGeneration;
    }

    /**
     * Gets the name of the application (name of the directory from which application was deployed.
     * Will return null if a problem occurred while getting metadata
     *
     * @return application name
     */
    // TODO: Remove after September 2019
    public String getApplicationName() { return applicationId.application().toString(); }

    /**
     * Gets the user who deployed the application.
     * Will return null if a problem occurred while getting metadata
     *
     * @return user name for the user who ran "deploy-application"
     */
    public String getDeployedByUser() {
        return deployedByUser;
    }

    /**
     * Gets the directory where the application was deployed from.
     * Will return null if a problem occurred while getting metadata
     *
     * @return path to raw deploy directory (for the original application)
     */
    public String getDeployPath() { return deployedFromDir; }

    public ApplicationId getApplicationId() { return applicationId; }

    /**
     * Gets the time the application was deployed
     * Will return null if a problem occurred while getting metadata
     *
     * @return timestamp for when "deploy-application" was run. In ms.
     */
    public Long getDeployTimestamp() { return deployTimestamp; }

    /**
     * Gets the time the application was deployed.
     * Will return null if a problem occurred while getting metadata
     *
     * @return timestamp for when "deploy-application" was run. In ms.
     */
    public Long getGeneration() { return generation; }

    /**
     * Returns whether this application generation was produced by a system internal redeployment,
     * not an application package change
     */
    public boolean isInternalRedeploy() { return internalRedeploy; }

    /** Returns an md5 hash of the contents of the application package */
    // TODO: Remove after September 2019
    public String getCheckSum() { return checksum; }

    /** Returns an md5 hash of the contents of the application package */
    public String getChecksum() { return checksum; }

    /** Returns the previously active generation at the point when this application was created. */
    public long getPreviousActiveGeneration() { return previousActiveGeneration; }

    @Override
    public String toString() {
        return deployedByUser + ", " + deployedFromDir + ", " + deployTimestamp + ", " + generation + ", " +
               checksum + ", " + previousActiveGeneration;
    }

    public static ApplicationMetaData fromJsonString(String jsonString) {
        try {
            Slime data = new Slime();
            new JsonDecoder().decode(data, Utf8.toBytes(jsonString));
            Inspector root = data.get();
            Inspector deploy = root.field("deploy");
            Inspector app = root.field("application");

            // TODO: Simplify to just ApplicationId.fromSerializedForm(app.field("id").asString()) after September 2019
            ApplicationId applicationId = app.field("id").valid() ?
                                          ApplicationId.fromSerializedForm(app.field("id").asString()) :
                                          ApplicationId.from(TenantName.defaultName(),
                                                             ApplicationName.from(app.field("name").asString()),
                                                             InstanceName.from("default"));

            return new ApplicationMetaData(deploy.field("user").asString(),
                                           deploy.field("from").asString(),
                                           deploy.field("timestamp").asLong(),
                                           booleanField("internalRedeploy", false, deploy),
                                           applicationId,
                                           app.field("checksum").asString(),
                                           app.field("generation").asLong(),
                                           app.field("previousActiveGeneration").asLong());
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing json metadata", e);
        }
    }

    public Slime getSlime() {
        Slime slime = new Slime();
        Cursor meta = slime.setObject();
        Cursor deploy = meta.setObject("deploy");
        deploy.setString("user", deployedByUser);
        deploy.setString("from", deployedFromDir);
        deploy.setLong("timestamp", deployTimestamp);
        deploy.setBool("internalRedeploy", internalRedeploy);
        Cursor app = meta.setObject("application");
        app.setString("id", applicationId.serializedForm());
        app.setString("name", applicationId.application().value()); // TODO: Remove after September 2019
        app.setString("checksum", checksum);
        app.setLong("generation", generation);
        app.setLong("previousActiveGeneration", previousActiveGeneration);
        return slime;
    }

    private static boolean booleanField(String fieldName, boolean defaultValue, Inspector object) {
        Inspector value = object.field(fieldName);
        if ( ! value.valid()) return defaultValue;
        return value.asBool();
    }

    public String asJsonString() {
        Slime slime = getSlime();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new JsonFormat(false).encode(baos, slime);
            return baos.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("Unable to encode metadata", e);
        }
    }

}
