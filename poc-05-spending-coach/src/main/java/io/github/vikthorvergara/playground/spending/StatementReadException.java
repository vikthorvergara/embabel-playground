package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.core.NonRetryable;

public class StatementReadException extends RuntimeException implements NonRetryable {

    public StatementReadException(String message, Throwable cause) {
        super(message, cause);
    }

    public StatementReadException(String message) {
        super(message);
    }
}
