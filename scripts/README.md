# `fortishield-indexer` packages generation guide

The packages' generation process consists on 2 steps:

- **Build**: compiles the Java application and bundles it into a package.
- **Assembly**: uses the package from the previous step and inflates it with plugins and
  configuration files, ready for production deployment.

We usually generate the packages using GitHub Actions, however, the process is designed to
be independent enough for maximum portability. GitHub Actions provides infrastructure, while
the building process is self-contained in the application code.

Each section includes instructions to generate packages locally, using Act or Docker.

- [Install Act](https://github.com/nektos/act)

## Build

...
...

#### Act (GitHub Workflow locally)

```console
act -j build -W .github/workflows/build.yml --artifact-server-path ./artifacts

[Build slim packages/build] 🏁  Job succeeded
```

#### Running in Docker

Using the [Docker environment](../docker):

```console
docker exec -it wi-build_$(<VERSION) bash scripts/build.sh -v 2.11.1 -s false -p linux -a {x64|arm64} -d {rpm|deb|tar}
```

The generated package is sent to `artifacts/`

## Assemble

**Note:** set the environment variable `TEST=true` to assemble a package with the required plugins only,
speeding up the assembly process.

<!--
### TAR
-->

### DEB

The script will:

- Extract the deb package using `ar` and `tar` tools.

  > By default, `ar` and `tar` tools expect the package to be in `fortishield-indexer/artifacts/tmp/deb`. The script takes care of creating the required folder structure, copying also the min package and the Makefile.

  Current folder loadout at this stage:

  ```
  artifacts/
  |-- dist
  |   |-- fortishield-indexer-min_4.9.0_amd64.deb
  `-- tmp
      `-- deb
          |-- Makefile
          |-- data.tar.gz
          |-- debmake_install.sh
          |-- etc
          |-- usr
          |-- var
          `-- fortishield-indexer-min_4.9.0_amd64.deb
  ```

  `usr`, `etc` and `var` folders contain `fortishield-indexer` files, extracted from `fortishield-indexer-min-*.deb`.
  `Makefile` and the `debmake_install` are copied over from `fortishield-indexer/distribution/packages/src/deb`.
  The `fortishield-indexer-performance-analyzer.service` file is also copied from the same folder. It is a dependency of the SPEC file.

- Install the plugins using the `opensearch-plugin` CLI tool.
- Set up configuration files.

  > Included in `min-package`. Default files are overwritten.

- Bundle a DEB file with `debmake` and the `Makefile`.

  > `debmake` and other dependencies can be installed using the provision.sh script. The
  > script is invoked by the GitHub Workflow.

  Current folder loadout at this stage:

  ```
  artifacts/
  |-- artifact_name.txt
  |-- dist
  |   |-- fortishield-indexer-min_4.9.0_amd64.deb
  |   `-- fortishield-indexer_4.9.0_amd64.deb
  `-- tmp
      `-- deb
          |-- Makefile
          |-- data.tar.gz
          |-- debmake_install.sh
          |-- etc
          |-- usr
          |-- var
          |-- fortishield-indexer-min_4.9.0_amd64.deb
          `-- debian/
              | -- control
              | -- copyright
              | -- rules
              | -- preinst
              | -- prerm
              | -- postinst
  ```

### Running in Act

```console
act -j assemble -W .github/workflows/build.yml --artifact-server-path ./artifacts --matrix distribution:deb --matrix architecture:x64 --var OPENSEARCH_VERSION=2.11.1

[Build slim packages/build] 🏁  Job succeeded
```

#### Running in Docker

Pre-requisites:

- Current directory: `fortishield-indexer/`
- Existing deb package in `fortishield-indexer/artifacts/dist/deb`, as a result of the _Build_ stage.
- Using the [Docker environment](../docker):

```console
docker exec -it wi-assemble_$(<VERSION) bash scripts/assemble.sh -v 2.11.1 -p linux -a x64 -d deb
```

### RPM

The `assemble.sh` script will use the output from the `build.sh` script and use it as a
base to bundle together a final package containing the plugins, the production configuration
and the service files.

The script will:

- Extract the rpm package using `rpm2cpio` and `cpio` tools.

  > By default, `rpm2cpio` and `cpio` tools expect the package to be in `fortishield-indexer/artifacts/tmp/rpm`. The script takes care of creating the required folder structure, copying also the min package and the SPEC file.

  Current folder loadout at this stage:

  ```
  /rpm/$ARCH
      /etc
      /usr
      /var
      fortishield-indexer-min-*.rpm
      fortishield-indexer.rpm.spec
  ```

  `usr`, `etc` and `var` folders contain `fortishield-indexer` files, extracted from `fortishield-indexer-min-*.rpm`.
  `fortishield-indexer.rpm.spec` is copied over from `fortishield-indexer/distribution/packages/src/rpm/fortishield-indexer.rpm.spec`.
  The `fortishield-indexer-performance-analyzer.service` file is also copied from the same folder. It is a dependency of the SPEC file.

- Install the plugins using the `opensearch-plugin` CLI tool.
- Set up configuration files.

  > Included in `min-package`. Default files are overwritten.

- Bundle an RPM file with `rpmbuild` and the SPEC file `fortishield-indexer.rpm.spec`.

  - `rpmbuild` is part of the `rpm` OS package.

  > `rpmbuild` is invoked from `fortishield-indexer/artifacts/tmp/rpm`. It creates the {BUILD,RPMS,SOURCES,SRPMS,SPECS,TMP} folders and applies the rules in the SPEC file. If successful, `rpmbuild` will generate the package in the `RPMS/` folder. The script will copy it to `fortishield-indexer/artifacts/dist` and clean: remove the `tmp\` folder and its contents.

  Current folder loadout at this stage:

  ```
  /rpm/$ARCH
      /{BUILD,RPMS,SOURCES,SRPMS,SPECS,TMP}
      /etc
      /usr
      /var
      fortishield-indexer-min-*.rpm
      fortishield-indexer.rpm.spec
  ```

### Running in Act

```console
act -j assemble -W .github/workflows/build.yml --artifact-server-path ./artifacts --matrix distribution:rpm --matrix architecture:x64 --var OPENSEARCH_VERSION=2.11.1

[Build slim packages/build] 🏁  Job succeeded
```

#### Running in Docker

Pre-requisites:

- Current directory: `fortishield-indexer/`
- Existing rpm package in `fortishield-indexer/artifacts/dist/rpm`, as a result of the _Build_ stage.
- Using the [Docker environment](../docker):

```console
docker exec -it wi-assemble_$(<VERSION) bash scripts/assemble.sh -v 2.11.1 -p linux -a x64 -d rpm
```
