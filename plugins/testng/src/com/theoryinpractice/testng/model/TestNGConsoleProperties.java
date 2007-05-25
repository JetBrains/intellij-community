package com.theoryinpractice.testng.model;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.Storage;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import org.jetbrains.annotations.NonNls;

public class TestNGConsoleProperties extends TestConsoleProperties {
    @NonNls private static final String PREFIX = "TestNGSupport.";
    private final TestNGConfiguration myConfiguration;

    public TestNGConsoleProperties(TestNGConfiguration config)
    {
        this(config, new Storage.PropertiesComponentStorage(PREFIX, PropertiesComponent.getInstance()));
    }

    public TestNGConsoleProperties(TestNGConfiguration config, Storage storage)
    {
        super(storage, config.getProject());
        myConfiguration = config;
    }


    public TestNGConfiguration getConfiguration()
    {
        return myConfiguration;
    }
}
