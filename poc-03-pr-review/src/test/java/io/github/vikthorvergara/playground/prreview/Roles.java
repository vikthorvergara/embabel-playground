package io.github.vikthorvergara.playground.prreview;

import com.embabel.common.ai.model.ByRoleModelSelectionCriteria;
import com.embabel.common.ai.model.LlmOptions;

final class Roles {

    private Roles() {
    }

    /** withLlmByRole puts the role in the selection criteria, not in LlmOptions.role. */
    static boolean selectsRole(LlmOptions options, String role) {
        return options.getCriteria() instanceof ByRoleModelSelectionCriteria criteria && criteria.getRole().equals(role);
    }
}
