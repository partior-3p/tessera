package com.quorum.tessera.config.cli;

import com.quorum.tessera.ServiceLoaderUtil;
import com.quorum.tessera.cli.CliException;
import com.quorum.tessera.cli.CliResult;
import com.quorum.tessera.cli.keypassresolver.CliKeyPasswordResolver;
import com.quorum.tessera.cli.keypassresolver.KeyPasswordResolver;
import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.util.JaxbUtil;
import com.quorum.tessera.reflect.ReflectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

@CommandLine.Command(
        name = "tessera",
        headerHeading = "Usage:%n%n",
        header = "Tessera private transaction manager for Quorum",
        synopsisHeading = "%n",
        descriptionHeading = "%nDescription:%n%n",
        description = "Start a Tessera node.  Other commands exist to manage Tessera encryption keys",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n",
        abbreviateSynopsis = true)
public class TesseraCommand implements Callable<CliResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TesseraCommand.class);

    private final Validator validator;

    private final KeyPasswordResolver keyPasswordResolver;

    public TesseraCommand() {
        this(ServiceLoaderUtil.load(KeyPasswordResolver.class).orElse(new CliKeyPasswordResolver()));
    }

    private TesseraCommand(final KeyPasswordResolver keyPasswordResolver) {
        this.keyPasswordResolver = Objects.requireNonNull(keyPasswordResolver);
        this.validator =
            Validation.byDefaultProvider().configure().ignoreXmlConfiguration().buildValidatorFactory().getValidator();
    }

    @CommandLine.Option(
            names = {"--configfile", "-configfile"},
            description = "Path to node configuration file")
    public Config config;

    @CommandLine.Option(
            names = {"--pidfile", "-pidfile"},
            description = "the path to write the PID to")
    public Path pidFilePath;

    @CommandLine.Option(
            names = {"-o", "--override"},
            paramLabel = "KEY=VALUE")
    private Map<String, String> overrides = new LinkedHashMap<>();

    @CommandLine.Option(
            names = {"-r", "--recover"},
            description = "Start Tessera in recovery mode")
    private boolean recover;

    @CommandLine.Mixin public DebugOptions debugOptions;

    @CommandLine.Unmatched public List<String> unmatchedEntries;

    // TODO(cjh) dry run option to print effective config to terminal to allow review of CLI overrides

    @Override
    public CliResult call() throws Exception {
        // we can't use required=true in the params for @Option as this also applies the requirement to all subcmds
        if (Objects.isNull(config)) {
            throw new NoTesseraConfigfileOptionException();
        }

        overrides.forEach((target, value) -> {
            LOGGER.debug("Setting : {} with value(s) {}", target, value);
            OverrideUtil.setValue(config, target, value);
            LOGGER.debug("Set : {} with value(s) {}", target, value);
        });

        if (recover) {
            config.setRecoveryMode(true);
        }

//            if (Objects.nonNull(parseResult.unmatched())) {
//                List<String> unmatched = new ArrayList<>(parseResult.unmatched());
//
//                for (int i = 0; i < unmatched.size(); i++) {
//                    String line = unmatched.get(i);
//                    if (line.startsWith("-")) {
//                        final String name = line.replaceFirst("-{1,2}", "");
//                        final int nextIndex = i + 1;
//                        if (nextIndex > (unmatched.size() - 1)) {
//                            break;
//                        }
//                        i = nextIndex;
//                        final String value = unmatched.get(nextIndex);
//                        try {
//                            OverrideUtil.setValue(config, name, value);
//                        } catch (ReflectException ex) {
//                            // Ignore error
//                            LOGGER.debug("", ex);
//                        }
//                    }
//                }
//            }

        final Set<ConstraintViolation<Config>> violations = validator.validate(config);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        keyPasswordResolver.resolveKeyPasswords(config);

        if (Objects.nonNull(pidFilePath)) {
            // TODO(cjh) duplication with PidFileMixin.class
            if (Files.exists(pidFilePath)) {
                LOGGER.info("File already exists {}", pidFilePath);
            } else {
                LOGGER.info("Created pid file {}", pidFilePath);
            }

            final String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

            try (OutputStream stream = Files.newOutputStream(pidFilePath, CREATE, TRUNCATE_EXISTING)) {
                stream.write(pid.getBytes(UTF_8));
            }
        }

        return new CliResult(0, false, config);
    }
}
