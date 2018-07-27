package com.alaptseu.apollo.graphql.client;

import com.apollographql.apollo.compiler.GraphQLCompiler;
import com.apollographql.apollo.compiler.NullableValueType;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ConfigurationBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.file.Files.walk;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.copyURLToFile;

/**
 * @author Alex L.
 */
@Mojo(name = "generate",
    requiresDependencyCollection = ResolutionScope.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    threadSafe = true
)
public class ApolloGraphQLMojo extends AbstractMojo {

    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/generated-sources/graphql-client")
    private File outputDirectory;

    @Parameter(property = "basePackage", defaultValue = "com.example.graphql.client")
    private String basePackage;

    @Parameter(property = "introspectionFile", defaultValue = "${project.basedir}/src/main/graphql/schema.json")
    private File introspectionFile;

    @Parameter(property = "addSourceRoot", defaultValue = "true")
    private boolean addSourceRoot;

    @Parameter(readonly = true, required = true, defaultValue = "${project}")
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final String sourceDirName = join(File.separator, "src", "main", "graphql");
        final File queryDir = new File(this.project.getBasedir(), sourceDirName);
        final String basePackageDirName = this.basePackage.replace('.', File.separatorChar);
        try {
            if (!queryDir.isDirectory()) {
                throw new IllegalArgumentException(format("%s must be a directory", queryDir.getAbsoluteFile()));
            }
            List<File> queries = walk(queryDir.toPath())
                .filter(path -> path.toFile().isFile() && path.toFile().getName().endsWith(".graphql"))
                .map(Path::toFile).collect(toList());

            if (queries == null || queries.isEmpty()) {
                throw new IllegalArgumentException(format("No queries found under %s", queryDir.getAbsolutePath()));
            }
            final File baseTargetDir = new File(this.project.getBuild().getDirectory(), join(File.separator,
                "graphql-schema", sourceDirName, basePackageDirName));
            final File schema = new File(baseTargetDir, "schema.json");
            File nodeModules = new File(project.getBuild().getDirectory(), join(File.separator,
                "apollo-codegen-node-modules", "node_modules"));
            deleteRecursively(nodeModules);
            nodeModules.mkdirs();

            Set<String> nodeModuleResources = new Reflections(new ConfigurationBuilder().setScanners(new ResourcesScanner())
                .setUrls(getClass().getResource("/node_modules")))
                .getResources(Pattern.compile(".*"));

            for (String resource : nodeModuleResources) {
                String path = resource.replaceFirst("/node_modules/", "").replace("/", File.separator);
                File diskPath = new File(nodeModules, path);
                diskPath.getParentFile().mkdirs();

                copyURLToFile(getClass().getResource(resource), diskPath);

            }
            File apolloCli = new File(nodeModules, join(File.separator, "apollo-codegen", "lib", "cli.js"));
            apolloCli.setExecutable(true);

            if (!this.introspectionFile.isFile()) {
                throw new IllegalArgumentException("Introspection JSON not found: ${introspectionFile.absolutePath}");
            }

            if (!apolloCli.isFile()) {
                throw new IllegalStateException("Apollo codegen cli not found: '${apolloCli.absolutePath}'");
            }
            schema.getParentFile().mkdirs();
            queries.forEach(query -> {
                    File src = new File(queryDir, query.getPath());
                    File dest = new File(baseTargetDir, query.getPath());
                    dest.getParentFile().mkdirs();

                    try {
                        Files.copy(Paths.get(src.getPath()), Paths.get(dest.getPath()));
                    } catch (IOException ex) {
                        getLog().error(ex);
                    }
                }
            );

            File node = findExecutableOnPath("node");
            getLog().info("Found node executable: ${node.absolutePath}");

            List<String> queriesList = queries.stream().map(file -> new File(baseTargetDir, file.getPath()).getAbsolutePath()).collect(Collectors.toList());
            List<String> arguments = asList("generate",
                join(" ", queriesList),
                "--target", "json", "--schema", introspectionFile.getAbsolutePath(), "--output", schema.getAbsolutePath());
            getLog().info(format("Running apollo cli %s with arguments: %s}", apolloCli.getAbsoluteFile(), join(" ", arguments)));

            Process proc = new ProcessBuilder(node.getAbsolutePath(), apolloCli.getAbsolutePath(), join(" ", arguments))
                .directory(nodeModules.getParentFile())
                .inheritIO()
                .start();

            if (proc.waitFor() != 0) {
                throw new IllegalStateException("Apollo codegen cli command failed");
            }

            GraphQLCompiler compiler = new GraphQLCompiler();
            compiler.write(new GraphQLCompiler.Arguments(schema, outputDirectory, new HashMap<>(), NullableValueType.JAVA_OPTIONAL, true, true));

            if(this.addSourceRoot) {
                project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
            }

        } catch (InterruptedException | IOException ex) {
            getLog().error(ex);
        }

    }

    private static boolean deleteRecursively(File dir) {
        File[] allContents = dir.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteRecursively(file);
            }
        }
        return dir.delete();
    }

    private static File findExecutableOnPath(String name) {
        for (String dirName : System.getenv("PATH").split(File.pathSeparator)) {
            File file = new File(dirName, name);
            if (file.isFile() && file.canExecute()) {
                return file;
            }
        }
        throw new IllegalArgumentException(format("should have found the executable %s in PATH", name));
    }


}
