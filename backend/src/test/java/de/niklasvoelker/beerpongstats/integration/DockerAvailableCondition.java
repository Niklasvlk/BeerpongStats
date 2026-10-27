package de.niklasvoelker.beerpongstats.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Überspringt Tests, die einen Postgres-Container brauchen, wenn Docker nicht läuft. Als Erweiterung an der Basisklasse
 * wird sie an alle Testklassen vererbt (@EnabledIf würde das nicht tun).
 */
class DockerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return DockerClientFactory.instance().isDockerAvailable()
                ? ConditionEvaluationResult.enabled("Docker ist verfügbar")
                : ConditionEvaluationResult.disabled("Docker ist nicht verfügbar");
    }
}
