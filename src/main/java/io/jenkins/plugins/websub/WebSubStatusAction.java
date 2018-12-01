package io.jenkins.plugins.websub;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.TransientProjectActionFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import lombok.val;

public class WebSubStatusAction<T extends Job<?, ?> & ParameterizedJob> implements Action {
    @Extension
    public static class Factory extends TransientProjectActionFactory {
        @Override
        public Collection<? extends Action> createFor(final AbstractProject project) {
            val trigger = (WebSubTrigger) project.getTriggers().get(WebSubTrigger.DESCRIPTOR);
            if (trigger == null) return Collections.emptyList();
            return Collections.singleton(WebSubStatusAction.from(project));
        }
    }

    private final T project;

    private WebSubStatusAction(final T project) {
        this.project = project;
    }

    private static <T extends Job<?, ?> & ParameterizedJob> WebSubStatusAction<T> from(final T project) {
        return new WebSubStatusAction<>(project);
    }

    private Optional<WebSubTrigger> getTrigger() {
        return Optional.ofNullable(project.getTriggers().get(WebSubTrigger.DESCRIPTOR))
                .map(WebSubTrigger.class::cast);
    }

    // Used by Jelly template.
    @SuppressWarnings("unused")
    public List<WebSubTriggerSubscription> getSubscriptions() {
        return getTrigger()
                .map(WebSubTrigger::getSubscriptions)
                .orElse(new ArrayList<>());
    }

    @Override
    public String getIconFileName() {
        return "/plugin/websub-trigger/icons/websub-24px.png";
    }

    @Override
    public String getDisplayName() {
        return "WebSub Trigger Status";
    }

    @Override
    public String getUrlName() {
        return "websub-trigger";
    }
}
