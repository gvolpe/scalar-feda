{
  description = "Scalar 2023 FEDA talk examples";

  inputs = {
    nixpkgs.url = github:nixos/nixpkgs/nixpkgs-unstable;
    flake-utils.url = github:numtide/flake-utils;
  };

  outputs = { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        jreOverlay = f: p: {
          jre = p.jdk19_headless;
        };

        nativeOverlay = f: p: {
          scala-cli-native = p.symlinkJoin
            {
              name = "scala-cli-native";
              paths = [ p.scala-cli ];
              buildInputs = [ p.makeWrapper ];
              postBuild = ''
                wrapProgram $out/bin/scala-cli \
                  --prefix LLVM_BIN : "${p.llvmPackages.clang}/bin"
              '';
            };
        };
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ jreOverlay nativeOverlay ];
        };

        mainApp = pkgs.writeShellScriptBin "scalar-feda-app" ''
          ${pkgs.scala-cli}/bin/scala-cli run .
        '';
      in
      {
        devShells.default = pkgs.mkShell {
          name = "scala-dev-shell";
          buildInputs = with pkgs; [ jre scala-cli ];
          JAVA_HOME = "${pkgs.jre}";
        };

        packages.default = mainApp;
      }
    );
}
