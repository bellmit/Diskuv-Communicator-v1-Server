// Copyright 2021 Diskuv, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.diskuv.communicator.configurator;

import com.diskuv.communicator.configurator.dropwizard.GenerateConfiguration;
import com.diskuv.communicator.configurator.dropwizard.ModifyConfiguration;
import com.diskuv.communicator.configurator.dropwizard.ViewClientConfiguration;
import com.diskuv.communicator.configurator.errors.PrintExceptionMessageHandler;
import picocli.CommandLine;

@CommandLine.Command(subcommands = {
        GenerateConfiguration.class,
        ModifyConfiguration.class,
        ViewClientConfiguration.class,
})
public class Configurator {
    public static void main(String... args) {
        int exitCode = new CommandLine(new Configurator()).setExecutionExceptionHandler(new PrintExceptionMessageHandler()).execute(args);
        System.exit(exitCode);
    }
}
