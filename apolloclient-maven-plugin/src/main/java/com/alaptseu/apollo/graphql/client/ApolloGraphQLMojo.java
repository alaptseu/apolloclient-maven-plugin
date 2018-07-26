package com.alaptseu.apollo.graphql.client;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * @author Alex L.
 */
@Mojo(name = "generate",
    requiresDependencyCollection = ResolutionScope.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    threadSafe = true
)
public class ApolloGraphQLMojo  extends AbstractMojo {

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

    }

}
