package com.haskforce.jps;

/*
 * Downloaded from https://github.com/Atsky/haskell-idea-plugin on 7 May
 * 2014.
 */

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Simple cabal interface.
 */
public class CabalJspInterface {
    @NotNull
    private File myCabalFile;
    @NotNull
    private String myCabalPath;

    CabalJspInterface(@NotNull String cabalPath, @NotNull File cabalFile) {
        myCabalPath = cabalPath;
        myCabalFile = cabalFile;
    }

    private Process runCommand(String command) throws IOException {
        return new ProcessWrapper(myCabalFile.getParentFile().getCanonicalPath()).
                            getProcess(myCabalPath, command);
    }

    public Process configure() throws IOException {
        return runCommand("configure");
    }

    public Process build() throws IOException {
        return runCommand("build");
    }

    public Process clean() throws IOException {
        return runCommand("clean");
    }
}