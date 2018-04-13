package com.netflix.vms.transformer.startup;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.cup.CupModule;
import com.netflix.runtime.health.guice.HealthModule;
import com.netflix.runtime.lifecycle.RuntimeCoreModule;
import com.netflix.vms.transformer.SimpleTransformerCycleInterrupter;
import com.netflix.vms.transformer.common.TransformerCycleInterrupter;
import com.netflix.vms.transformer.common.VersionMinter;
import com.netflix.vms.transformer.common.cassandra.TransformerCassandraHelper;
import com.netflix.vms.transformer.common.config.OctoberSkyData;
import com.netflix.vms.transformer.common.config.TransformerConfig;
import com.netflix.vms.transformer.common.cup.CupLibrary;
import com.netflix.vms.transformer.cup.CupLibraryImpl;
import com.netflix.vms.transformer.health.TransformerServerHealthIndicator;
import com.netflix.vms.transformer.octobersky.OctoberSkyDataImpl;
import com.netflix.vms.transformer.publish.workflow.util.TransformerServerCassandraHelper;
import com.netflix.vms.transformer.util.SequenceVersionMinter;
import javax.inject.Singleton;

// Common module dependencies
// Server dependencies


/**
 * This is the "main" module where we wire everything up. If you see this module getting overly
 * complex, it's a good idea to break things off into separate ones and install them here instead.
 *
 * @author This file is auto-generated by runtime@netflix.com. Feel free to modify.
 */
public final class TransformerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new RuntimeCoreModule());
        install(new HealthModule() {
            @Override
            protected void configureHealth() {
                bindAdditionalHealthIndicator().to(TransformerServerHealthIndicator.class);
            }
        });
        install(new JerseyModule());
        install(new CupModule());

        bind(OctoberSkyData.class).to(OctoberSkyDataImpl.class);
        bind(CupLibrary.class).to(CupLibraryImpl.class);
        bind(TransformerCycleInterrupter.class).to(SimpleTransformerCycleInterrupter.class);
        bind(TransformerCassandraHelper.class).to(TransformerServerCassandraHelper.class);
        bind(TransformerCycleKickoff.class).asEagerSingleton();
        bind(VersionMinter.class).annotatedWith(Names.named("vipAnnounceID")).toInstance(new SequenceVersionMinter());
    }

    @Provides
    @Singleton
    TransformerConfig getTransformerConfig(ConfigProxyFactory factory) {
        // Here we turn the config interface into an implementation that can load dynamic properties.
        return factory.newProxy(TransformerConfig.class);
    }
}
