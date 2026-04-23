package org.example.amortizationhelper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpStdioClientProperties.Parameters;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.File;
import java.util.List;
import java.util.Locale;

@Configuration
public class McpStdioCommandConfig {

    private static final Logger log = LoggerFactory.getLogger(McpStdioCommandConfig.class);

    @Bean
    static BeanPostProcessor mcpStdioCommandPostProcessor(Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof McpStdioClientProperties properties) {
                    properties.getConnections().replaceAll((name, parameters) ->
                            normalizeCommand(environment, name, parameters));
                }
                return bean;
            }
        };
    }

    private static Parameters normalizeCommand(Environment environment, String connectionName, Parameters parameters) {
        String directWeatherCommand = environment.getProperty("app.mcp.weather.direct-command", "");
        if ("weather".equals(connectionName) && !directWeatherCommand.isBlank()) {
            log.info("Using direct MCP stdio command '{}' for connection '{}'.", directWeatherCommand, connectionName);
            return new Parameters(directWeatherCommand, List.of(), parameters == null ? null : parameters.env());
        }

        if (isWindows()) {
            return windowsNpxCommand(connectionName, parameters);
        }

        return parameters;
    }

    private static Parameters windowsNpxCommand(String connectionName, Parameters parameters) {
        if (parameters == null || parameters.command() == null || !"npx".equalsIgnoreCase(parameters.command())) {
            return parameters;
        }

        String command = findOnPath("npx.cmd");
        log.info("Using '{}' for MCP stdio connection '{}' on Windows.", command, connectionName);
        return new Parameters(command, parameters.args(), parameters.env());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    private static String findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return executable;
        }

        for (String entry : path.split(File.pathSeparator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            File candidate = new File(entry, executable);
            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }

        return executable;
    }
}
