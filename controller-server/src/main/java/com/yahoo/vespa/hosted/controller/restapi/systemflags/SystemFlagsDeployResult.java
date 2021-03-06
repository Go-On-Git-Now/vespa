// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireSystemFlagsDeployResult;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireSystemFlagsDeployResult.WireFlagDataChange;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireSystemFlagsDeployResult.WireOperationFailure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
class SystemFlagsDeployResult {

    private final List<FlagDataChange> flagChanges;
    private final List<OperationError> errors;

    SystemFlagsDeployResult(List<FlagDataChange> flagChanges, List<OperationError> errors) {
        this.flagChanges = flagChanges;
        this.errors = errors;
    }

    SystemFlagsDeployResult(List<OperationError> errors) {
        this(List.of(), errors);
    }

    List<FlagDataChange> flagChanges() {
        return flagChanges;
    }

    List<OperationError> errors() {
        return errors;
    }

    static SystemFlagsDeployResult merge(List<SystemFlagsDeployResult> results) {
        List<FlagDataChange> mergedChanges = mergeChanges(results);
        List<OperationError> mergedErrors = mergeErrors(results);
        return new SystemFlagsDeployResult(mergedChanges, mergedErrors);
    }

    private static List<OperationError> mergeErrors(List<SystemFlagsDeployResult> results) {
        Map<OperationErrorWithoutTarget, Set<FlagsTarget>> targetsForError = new HashMap<>();
        for (SystemFlagsDeployResult result : results) {
            for (OperationError error : result.errors()) {
                var errorWithoutTarget = new OperationErrorWithoutTarget(error);
                targetsForError.computeIfAbsent(errorWithoutTarget, k -> new HashSet<>())
                        .addAll(error.targets());
            }
        }
        List<OperationError> mergedErrors = new ArrayList<>();
        targetsForError.forEach(
                (error, targets) -> mergedErrors.add(error.toOperationError(targets)));
        return mergedErrors;
    }

    private static List<FlagDataChange> mergeChanges(List<SystemFlagsDeployResult> results) {
        Map<FlagDataChangeWithoutTarget, Set<FlagsTarget>> targetsForChange = new HashMap<>();
        for (SystemFlagsDeployResult result : results) {
            for (FlagDataChange change : result.flagChanges()) {
                var changeWithoutTarget = new FlagDataChangeWithoutTarget(change);
                targetsForChange.computeIfAbsent(changeWithoutTarget, k -> new HashSet<>())
                        .addAll(change.targets());
            }
        }
        List<FlagDataChange> mergedChanges = new ArrayList<>();
        targetsForChange.forEach(
                (change, targets) -> mergedChanges.add(change.toFlagDataChange(targets)));
        return mergedChanges;
    }


    WireSystemFlagsDeployResult toWire() {
        var wireResult = new WireSystemFlagsDeployResult();
        wireResult.changes = new ArrayList<>();
        for (FlagDataChange change : flagChanges) {
            var wireChange = new WireFlagDataChange();
            wireChange.flagId = change.flagId().toString();
            wireChange.operation = change.operation().asString();
            wireChange.targets = change.targets().stream().map(FlagsTarget::asString).collect(toList());
            wireChange.data = change.data().map(FlagData::toWire).orElse(null);
            wireChange.previousData = change.previousData().map(FlagData::toWire).orElse(null);
            wireResult.changes.add(wireChange);
        }
        wireResult.errors = new ArrayList<>();
        for (OperationError error : errors) {
            var wireError = new WireOperationFailure();
            wireError.message = error.message();
            wireError.operation = error.operation().asString();
            wireError.targets = error.targets().stream().map(FlagsTarget::asString).collect(toList());
            wireError.flagId = error.flagId().map(FlagId::toString).orElse(null);
            wireError.data = error.flagData().map(FlagData::toWire).orElse(null);
            wireResult.errors.add(wireError);
        }
        return wireResult;
    }

    static class FlagDataChange {

        private final FlagId flagId;
        private final Set<FlagsTarget> targets;
        private final OperationType operationType;
        private final FlagData data;
        private final FlagData previousData;

        private FlagDataChange(
                FlagId flagId, Set<FlagsTarget> targets, OperationType operationType, FlagData data, FlagData previousData) {
            this.flagId = flagId;
            this.targets = targets;
            this.operationType = operationType;
            this.data = data;
            this.previousData = previousData;
        }

        static FlagDataChange created(FlagId flagId, FlagsTarget target, FlagData data) {
            return new FlagDataChange(flagId, Set.of(target), OperationType.CREATE, data, null);
        }

        static FlagDataChange deleted(FlagId flagId, FlagsTarget target) {
            return new FlagDataChange(flagId, Set.of(target), OperationType.DELETE, null, null);
        }

        static FlagDataChange updated(FlagId flagId, FlagsTarget target, FlagData data, FlagData previousData) {
            return new FlagDataChange(flagId, Set.of(target), OperationType.UPDATE, data, previousData);
        }

        FlagId flagId() {
            return flagId;
        }

        Set<FlagsTarget> targets() {
            return targets;
        }

        OperationType operation() {
            return operationType;
        }

        Optional<FlagData> data() {
            return Optional.ofNullable(data);
        }

        Optional<FlagData> previousData() {
            return Optional.ofNullable(previousData);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FlagDataChange that = (FlagDataChange) o;
            return Objects.equals(flagId, that.flagId) &&
                    Objects.equals(targets, that.targets) &&
                    operationType == that.operationType &&
                    Objects.equals(data, that.data) &&
                    Objects.equals(previousData, that.previousData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flagId, targets, operationType, data, previousData);
        }

        @Override
        public String toString() {
            return "FlagDataChange{" +
                    "flagId=" + flagId +
                    ", targets=" + targets +
                    ", operationType=" + operationType +
                    ", data=" + data +
                    ", previousData=" + previousData +
                    '}';
        }
    }

    static class OperationError {

        final String message;
        final Set<FlagsTarget> targets;
        final OperationType operation;
        final FlagId flagId;
        final FlagData flagData;

        private OperationError(
                String message, Set<FlagsTarget> targets, OperationType operation, FlagId flagId, FlagData flagData) {
            this.message = message;
            this.targets = targets;
            this.operation = operation;
            this.flagId = flagId;
            this.flagData = flagData;
        }

        static OperationError listFailed(String message, FlagsTarget target) {
            return new OperationError(message, Set.of(target), OperationType.LIST, null, null);
        }

        static OperationError createFailed(String message, FlagsTarget target, FlagData flagData) {
            return new OperationError(message, Set.of(target), OperationType.CREATE, flagData.id(), flagData);
        }

        static OperationError updateFailed(String message, FlagsTarget target, FlagData flagData) {
            return new OperationError(message, Set.of(target), OperationType.UPDATE, flagData.id(), flagData);
        }

        static OperationError deleteFailed(String message, FlagsTarget target, FlagId id) {
            return new OperationError(message, Set.of(target), OperationType.DELETE, id, null);
        }

        String message() { return message; }
        Set<FlagsTarget> targets() { return targets; }
        OperationType operation() { return operation; }
        Optional<FlagId> flagId() { return Optional.ofNullable(flagId); }
        Optional<FlagData> flagData() { return Optional.ofNullable(flagData); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OperationError that = (OperationError) o;
            return Objects.equals(message, that.message) &&
                    Objects.equals(targets, that.targets) &&
                    operation == that.operation &&
                    Objects.equals(flagId, that.flagId) &&
                    Objects.equals(flagData, that.flagData);
        }

        @Override public int hashCode() { return Objects.hash(message, targets, operation, flagId, flagData); }

        @Override
        public String toString() {
            return "OperationFailure{" +
                    "message='" + message + '\'' +
                    ", targets=" + targets +
                    ", operation=" + operation +
                    ", flagId=" + flagId +
                    ", flagData=" + flagData +
                    '}';
        }
    }

    enum OperationType {
        CREATE("create"), DELETE("delete"), UPDATE("update"), LIST("list");

        private final String stringValue;

        OperationType(String stringValue) { this.stringValue = stringValue; }

        String asString() { return stringValue; }
    }

    private static class FlagDataChangeWithoutTarget {
        final FlagId flagId;
        final OperationType operationType;
        final FlagData data;
        final FlagData previousData;
        final JsonNode jsonData; // needed for FlagData equality check
        final JsonNode jsonPreviousData; // needed for FlagData equality check


        FlagDataChangeWithoutTarget(FlagDataChange change) {
            this.flagId = change.flagId();
            this.operationType = change.operation();
            this.data = change.data().orElse(null);
            this.previousData = change.previousData().orElse(null);
            this.jsonData = Optional.ofNullable(data).map(FlagData::toJsonNode).orElse(null);
            this.jsonPreviousData = Optional.ofNullable(previousData).map(FlagData::toJsonNode).orElse(null);
        }

        FlagDataChange toFlagDataChange(Set<FlagsTarget> targets) {
            return new FlagDataChange(flagId, targets, operationType, data, previousData);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FlagDataChangeWithoutTarget that = (FlagDataChangeWithoutTarget) o;
            return Objects.equals(flagId, that.flagId) &&
                    operationType == that.operationType &&
                    Objects.equals(jsonData, that.jsonData) &&
                    Objects.equals(jsonPreviousData, that.jsonPreviousData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flagId, operationType, jsonData, jsonPreviousData);
        }
    }

    private static class OperationErrorWithoutTarget {
        final String message;
        final OperationType operation;
        final FlagId flagId;
        final FlagData flagData;

        OperationErrorWithoutTarget(OperationError operationError) {
            this.message = operationError.message();
            this.operation = operationError.operation();
            this.flagId = operationError.flagId().orElse(null);
            this.flagData = operationError.flagData().orElse(null);
        }

        OperationError toOperationError(Set<FlagsTarget> targets) {
            return new OperationError(message, targets, operation, flagId, flagData);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OperationErrorWithoutTarget that = (OperationErrorWithoutTarget) o;
            return Objects.equals(message, that.message) &&
                    operation == that.operation &&
                    Objects.equals(flagId, that.flagId) &&
                    Objects.equals(flagData, that.flagData);
        }

        @Override public int hashCode() { return Objects.hash(message, operation, flagId, flagData); }
    }
}
