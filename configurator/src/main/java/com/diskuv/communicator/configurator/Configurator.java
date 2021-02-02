package com.diskuv.communicator.configurator;

import com.diskuv.communicator.configurator.errors.PrintExceptionMessageHandler;
import picocli.CommandLine;

@CommandLine.Command(subcommands = {
        GenerateConfiguration.class,
        ModifyConfiguration.class
})
public class Configurator {
    public static void main(String... args) {
        int exitCode = new CommandLine(new Configurator()).setExecutionExceptionHandler(new PrintExceptionMessageHandler()).execute(args);
        System.exit(exitCode);
    }
}
