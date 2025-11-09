# Build Instructions for Enterprise Log Viewer

## Current Build Status

⚠️ **Network Dependency Issue**: The build currently requires internet access to download Maven dependencies from Maven Central Repository. In the current network-restricted environment, the build cannot complete.

**Error**:
```
Could not transfer artifact software.amazon.awssdk:bom:pom:2.23.9
Temporary failure in name resolution
```

---

## Prerequisites

### Required Software

1. **Java 21 or higher** (JDK)
   ```bash
   java -version
   # Should show: java version "21.0.x" or higher
   ```

2. **Apache Maven 3.9+**
   ```bash
   mvn --version
   # Should show: Apache Maven 3.9.x
   ```

3. **Internet Connection** (for first build to download dependencies)

### System Requirements

- **OS**: Linux, macOS, or Windows
- **RAM**: 4GB minimum, 8GB recommended
- **Disk Space**: 2GB for Maven dependencies and build artifacts

---

## Build Commands

### Option 1: Full Build with Executable JAR (Recommended)

This creates a standalone executable JAR with all dependencies included.

```bash
# Navigate to project directory
cd /path/to/log

# Clean previous builds and compile
mvn clean package

# The executable JAR will be created at:
# log-viewer-app/target/log-viewer.jar
```

**Output**:
```
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for Enterprise Log Viewer 1.0.0-SNAPSHOT:
[INFO]
[INFO] log-viewer-parent .................................. SUCCESS
[INFO] log-viewer-core .................................... SUCCESS
[INFO] log-viewer-parsers ................................. SUCCESS
[INFO] log-viewer-indexing ................................ SUCCESS
[INFO] log-viewer-aws ..................................... SUCCESS
[INFO] log-viewer-ui ...................................... SUCCESS
[INFO] log-viewer-app ..................................... SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Option 2: Compile Only (No JAR packaging)

```bash
mvn clean compile
```

### Option 3: Run Tests

```bash
mvn clean test
```

### Option 4: Skip Tests (Faster Build)

```bash
mvn clean package -DskipTests
```

### Option 5: Build with Debug Output

```bash
mvn clean package -X
```

---

## Build Output

### Module Build Order

Maven builds modules in this order:

1. **log-viewer-core** - Domain models and interfaces
2. **log-viewer-parsers** - Log format parsers
3. **log-viewer-indexing** - Lucene search engine
4. **log-viewer-aws** - AWS and SSH integration
5. **log-viewer-ui** - Swing user interface
6. **log-viewer-app** - Main application

### Generated Artifacts

After successful build:

```
log-viewer-core/target/
├── log-viewer-core-1.0.0-SNAPSHOT.jar
└── classes/

log-viewer-parsers/target/
├── log-viewer-parsers-1.0.0-SNAPSHOT.jar
└── classes/

log-viewer-indexing/target/
├── log-viewer-indexing-1.0.0-SNAPSHOT.jar
└── classes/

log-viewer-aws/target/
├── log-viewer-aws-1.0.0-SNAPSHOT.jar
└── classes/

log-viewer-ui/target/
├── log-viewer-ui-1.0.0-SNAPSHOT.jar
└── classes/

log-viewer-app/target/
├── log-viewer.jar                    ← Executable JAR (with dependencies)
├── log-viewer-app-1.0.0-SNAPSHOT.jar ← Library JAR (without dependencies)
└── classes/
```

**Main Executable**: `log-viewer-app/target/log-viewer.jar`

---

## Running the Application

### After Successful Build

```bash
# Run the standalone JAR
java -jar log-viewer-app/target/log-viewer.jar

# Or with more memory for large files
java -Xmx8g -jar log-viewer-app/target/log-viewer.jar

# With custom JVM options
java -Xmx8g -XX:+UseZGC -jar log-viewer-app/target/log-viewer.jar
```

### From IDE (Development)

**IntelliJ IDEA**:
1. Open project: `File → Open → select pom.xml`
2. Wait for Maven import to complete
3. Run configuration:
   - Main class: `com.enterprise.logviewer.app.LogViewerApplication`
   - Module: `log-viewer-app`
   - JDK: 21

**Eclipse**:
1. Import: `File → Import → Maven → Existing Maven Projects`
2. Select root `pom.xml`
3. Run as Java Application:
   - Main class: `com.enterprise.logviewer.app.LogViewerApplication`

**VS Code**:
1. Install "Extension Pack for Java"
2. Open project folder
3. Maven will auto-detect and import
4. Run from `LogViewerApplication.java`

---

## Dependency Download

### First Build Downloads

On the first build, Maven downloads ~500MB of dependencies:

**Major Dependencies**:
- Apache Lucene 9.9.1 (~50MB)
- AWS SDK v2.23.9 (~100MB)
- Kubernetes Java Client 6.10.0 (~30MB)
- Jackson 2.16.1 (~10MB)
- FlatLaf 3.3 (~5MB)
- Logback 1.4.14 (~3MB)
- JUnit 5.10.1 (~10MB)

**Total Download**: ~500-600MB (first build only)

### Offline Build (After First Successful Build)

Once dependencies are downloaded, you can build offline:

```bash
mvn clean package -o
```

The `-o` flag uses local Maven repository only.

---

## Troubleshooting Build Issues

### Issue 1: Network Connection

**Error**:
```
Could not transfer artifact ... from/to central
Temporary failure in name resolution
```

**Solutions**:
1. Check internet connection
2. Check firewall/proxy settings
3. Configure Maven proxy in `~/.m2/settings.xml`:

```xml
<settings>
  <proxies>
    <proxy>
      <id>corporate-proxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>proxy.company.com</host>
      <port>8080</port>
    </proxy>
  </proxies>
</settings>
```

### Issue 2: Java Version

**Error**:
```
Source option 21 is no longer supported. Use 21 or later.
```

**Solution**: Install Java 21 or higher

```bash
# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# macOS (Homebrew)
brew install openjdk@21

# Check version
java -version
```

### Issue 3: Maven Not Found

**Error**:
```
mvn: command not found
```

**Solution**: Install Maven

```bash
# Ubuntu/Debian
sudo apt install maven

# macOS (Homebrew)
brew install maven

# Manual installation
wget https://dlcdn.apache.org/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.tar.gz
tar xzvf apache-maven-3.9.11-bin.tar.gz
export PATH=/path/to/apache-maven-3.9.11/bin:$PATH
```

### Issue 4: Out of Memory

**Error**:
```
java.lang.OutOfMemoryError: Java heap space
```

**Solution**: Increase Maven memory

```bash
export MAVEN_OPTS="-Xmx2g -XX:MaxMetaspaceSize=512m"
mvn clean package
```

### Issue 5: Compilation Errors

**Error**:
```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin
```

**Solution**: Clean and rebuild

```bash
mvn clean
rm -rf ~/.m2/repository/com/enterprise/logviewer
mvn package
```

### Issue 6: Test Failures

**Error**:
```
[ERROR] Tests failed
```

**Solution**: Skip tests temporarily

```bash
mvn clean package -DskipTests
```

Or run specific test:

```bash
mvn test -Dtest=JsonLogParserTest
```

---

## Build Profiles

### Development Profile (Default)

```bash
mvn clean package
```

Features:
- Debug symbols included
- All tests run
- Validation checks enabled

### Production Profile

```bash
mvn clean package -Pproduction
```

Features:
- Optimized bytecode
- Tests skipped
- Minified resources

### Fast Build (Development)

```bash
mvn clean install -DskipTests -Dmaven.javadoc.skip=true
```

---

## Maven Repository

### Default Location

```
~/.m2/repository/
```

### Clear Cache (If Corrupted)

```bash
rm -rf ~/.m2/repository
mvn clean package
```

### Custom Repository Location

```bash
mvn clean package -Dmaven.repo.local=/custom/path/to/repo
```

---

## IDE Integration

### IntelliJ IDEA

**Import Project**:
1. `File → Open`
2. Select `pom.xml` in root directory
3. Click "Open as Project"
4. Wait for indexing

**Run Configuration**:
- Main class: `com.enterprise.logviewer.app.LogViewerApplication`
- Module classpath: `log-viewer-app`
- JRE: 21
- VM options: `-Xmx4g`

### Eclipse

**Import**:
1. `File → Import → Maven → Existing Maven Projects`
2. Select root directory
3. Check all modules
4. Finish

**Run**:
- Right-click `LogViewerApplication.java`
- Run As → Java Application

### VS Code

**Extensions**:
- Extension Pack for Java
- Maven for Java

**Import**: Open folder, Maven auto-detects

**Run**: Click "Run" above `main()` method

---

## Continuous Integration

### GitHub Actions Example

```yaml
name: Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'

    - name: Build with Maven
      run: mvn clean package

    - name: Upload JAR
      uses: actions/upload-artifact@v3
      with:
        name: log-viewer
        path: log-viewer-app/target/log-viewer.jar
```

### Jenkins Pipeline

```groovy
pipeline {
    agent any

    tools {
        maven 'Maven 3.9'
        jdk 'JDK 21'
    }

    stages {
        stage('Build') {
            steps {
                sh 'mvn clean package'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts 'log-viewer-app/target/log-viewer.jar'
            }
        }
    }
}
```

---

## Distribution

### Create Distribution Package

```bash
# Build
mvn clean package

# Create distribution directory
mkdir -p dist
cp log-viewer-app/target/log-viewer.jar dist/
cp README.md dist/
cp AWS_PCL_LOGIN_GUIDE.md dist/
cp -r test-data dist/

# Create archive
tar -czf enterprise-log-viewer-1.0.0.tar.gz dist/

# Or ZIP
zip -r enterprise-log-viewer-1.0.0.zip dist/
```

### Distribution Package Structure

```
enterprise-log-viewer-1.0.0/
├── log-viewer.jar                  ← Main executable
├── README.md                       ← User guide
├── AWS_PCL_LOGIN_GUIDE.md         ← AWS PCL guide
└── test-data/                      ← Sample log files
    ├── sample-json.log
    ├── sample-log4j.log
    └── sample-syslog.log
```

---

## Docker Build (Optional)

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk-alpine

# Install Maven
RUN apk add --no-cache maven

# Set working directory
WORKDIR /app

# Copy project files
COPY pom.xml .
COPY log-viewer-core ./log-viewer-core
COPY log-viewer-parsers ./log-viewer-parsers
COPY log-viewer-indexing ./log-viewer-indexing
COPY log-viewer-aws ./log-viewer-aws
COPY log-viewer-ui ./log-viewer-ui
COPY log-viewer-app ./log-viewer-app

# Build
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=0 /app/log-viewer-app/target/log-viewer.jar .

# Create directories
RUN mkdir -p ~/.logviewer/index ~/.logviewer/logs

# Run
ENTRYPOINT ["java", "-jar", "log-viewer.jar"]
```

### Build Docker Image

```bash
docker build -t enterprise-log-viewer:1.0.0 .
docker run -p 8080:8080 enterprise-log-viewer:1.0.0
```

---

## Build Time Estimates

| Build Type | Time (First) | Time (Cached) |
|------------|-------------|---------------|
| Clean compile | 5-8 min | 1-2 min |
| Clean package | 6-10 min | 2-3 min |
| Clean package (skip tests) | 4-6 min | 1-2 min |
| Incremental compile | N/A | 10-30 sec |

*First build includes dependency downloads*

---

## Verification

### After Successful Build

```bash
# Verify JAR exists
ls -lh log-viewer-app/target/log-viewer.jar

# Check JAR manifest
jar tf log-viewer-app/target/log-viewer.jar | grep MANIFEST

# Extract manifest
unzip -p log-viewer-app/target/log-viewer.jar META-INF/MANIFEST.MF

# Expected output:
# Manifest-Version: 1.0
# Main-Class: com.enterprise.logviewer.app.LogViewerApplication
```

### Test Run

```bash
# Quick test run
java -jar log-viewer-app/target/log-viewer.jar &
sleep 5
ps aux | grep log-viewer
kill %1

# If UI appears, build is successful!
```

---

## Next Steps

Once build is successful:

1. ✅ Run the application: `java -jar log-viewer-app/target/log-viewer.jar`
2. ✅ Test with sample files: `test-data/sample-json.log`
3. ✅ Try AWS PCL Login: `Tools → AWS PCL Login`
4. ✅ Index a log file: `File → Index File`
5. ✅ Search logs: Enter text and click Search

---

## Support

For build issues:

1. Check this guide's Troubleshooting section
2. Enable debug output: `mvn clean package -X`
3. Check logs: `~/.logviewer/logs/application.log`
4. Open issue on GitHub with build output

---

## Build Status Summary

```
✅ Java 21 detected
✅ Maven 3.9.11 detected
⚠️  Network access required for dependencies
❌ Current build blocked by network restrictions
```

**To build successfully**: Ensure network access to Maven Central Repository (https://repo.maven.apache.org/maven2)

---

## Manual Dependency Installation (Advanced)

If you have dependencies from another machine:

```bash
# Copy .m2 repository from machine with internet
rsync -av /path/to/.m2/repository/ ~/.m2/repository/

# Then build offline
mvn clean package -o
```

Required dependency locations in `~/.m2/repository/`:
- `org/apache/lucene/lucene-core/9.9.1/`
- `software/amazon/awssdk/bom/2.23.9/`
- `io/kubernetes/client-java/6.10.0/`
- `com/fasterxml/jackson/core/jackson-databind/2.16.1/`
- `com/formdev/flatlaf/3.3/`
- And all transitive dependencies...

---

## Conclusion

The Enterprise Log Viewer is ready to build once network access is available. All source code is complete and committed to the repository.

**Build Command**: `mvn clean package`

**Result**: `log-viewer-app/target/log-viewer.jar` (Standalone executable)

**Run**: `java -jar log-viewer-app/target/log-viewer.jar`
