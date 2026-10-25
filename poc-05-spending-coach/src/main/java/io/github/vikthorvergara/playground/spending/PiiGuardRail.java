package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.api.validation.guardrails.UserInputGuardRail;
import com.embabel.agent.core.Blackboard;
import com.embabel.common.core.validation.ValidationError;
import com.embabel.common.core.validation.ValidationResult;
import com.embabel.common.core.validation.ValidationSeverity;

import java.util.List;

public final class PiiGuardRail implements UserInputGuardRail {

    @Override
    public String getName() {
        return "pii";
    }

    @Override
    public String getDescription() {
        return "Blocks prompts that still carry an account, card or tax number";
    }

    @Override
    public ValidationResult validate(String prompt, Blackboard blackboard) {
        if (!Pii.found(prompt)) {
            return new ValidationResult(true, List.of());
        }
        return new ValidationResult(false,
                List.of(new ValidationError("pii", "personal identifier in prompt", ValidationSeverity.CRITICAL)));
    }
}
