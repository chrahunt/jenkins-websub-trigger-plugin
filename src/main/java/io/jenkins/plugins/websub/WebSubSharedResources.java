package io.jenkins.plugins.websub;

import io.jenkins.plugins.websub.subscriber.WebSubSubscriber;
import io.jenkins.plugins.websub.subscriber.WebSubSubscriptionRegistry;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
class WebSubSharedResources {
    private static WebSubSharedResources instance;
    public static WebSubSharedResources getInstance() {
        if (instance == null)
            instance = new WebSubSharedResources();
        return instance;
    }

    @Getter @Setter private WebSubSubscriber client;
    @Getter @Setter private WebSubSubscriptionRegistry registry;
}
