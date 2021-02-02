package com.diskuv.communicator.configurator.errors;

import picocli.CommandLine;

public class PrintExceptionMessageHandler implements CommandLine.IExecutionExceptionHandler {
    public int handleExecutionException(Exception ex,
                                        CommandLine cmd,
                                        CommandLine.ParseResult parseResult) throws Exception {
        if (ex instanceof UserException) {
            // bold red error message
            cmd.getErr().println(cmd.getColorScheme().errorText(ex.getMessage()));

            return cmd.getExitCodeExceptionMapper() != null
                    ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
                    : cmd.getCommandSpec().exitCodeOnExecutionException();
        }
        throw ex;
    }
}