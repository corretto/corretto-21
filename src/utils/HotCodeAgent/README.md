## Hot Code Heap Agent

HotCodeHeap is used to store actively used Java methods. Code stored in HotCodeHeap
is only removed when it is not used for a long time or gets deoptimized.
This agent is used to automate code placement into HotCodeHeap. The agent only works with JDK 21.

### Building

`mvn clean package`

This will create `hca-1.0-SNAPSHOT.jar` in the `target` directory.

### Running Tests

`mvn test`

### Usage

`java -javaagent:<PATH_TO_LIB>/hca-1.0-SNAPSHOT.jar`

### Configuration

The agent uses JFR events to determine actively used methods.
The agent creates temporary files with compiler directives in "java.io.tmpdir"
that are used to place the code into HotCodeHeap.

The agent can be configured as follows: `-javaagent:<PATH_TO_LIB>/hca-1.0-SNAPSHOT.jar=<opt1>=<opt1_value>,<opt2>=<opt2_value>`

The agent supports the following options:

- `config`: Path to the configuration file. The configuration file is a Java properties file. Any option provided in the command line will override the same option from the configuration file. For example the logging level will be `FINE` in this case: `-javaagent:HotCodeHeap-1.0.jar=config=agent.conf,logging.level=FINE`. `agent.conf`:

```properties
logging.level=INFO
```

- `logging.*`: Logging options. The agent uses `java.util.logging.Logger` for logging. The following options are supported:
  - `level`: Logging level. Default is `OFF`. Acceptable values are those defined in `java.util.logging.Level`.
  - `file`: Path to the log file. Default is none.

- `profiling.delay`: Delay when the agent starts profiling, e.g. `1000ms`, `10s`, `1m`, `1h`. Default is none.

- `profiling.methodExcludeList`: Path to the list of Java methods that should be excluded from HotCodeHeap. Default is none. A format of the exclude list: each line of the list is `package.class_name.method_name`. In any of three parts (package, class name, method name) a wildcard character `*` may be used. Examples:
   - `java.lang.*.*`: exclude all methods of classes from the `java.lang` package.
   - `java.lang.*.toString`: exclude `toString` methods of all classes from the `java.lang` package.
   - `*.toString`: exclude all `toString` methods of any class.
   - `*.get*`: exclude all methods which start with `get`.
   - `java.lang.Integer.*Value`: exclude methods of `java.lang.Integer` which end with `Value`.
   - `com.oracle.truffle.runtime.OptimizedCallTarget.*`: exclude all methods of `OptimizedCallTarget` class from `com.oracle.truffle.runtime` package.

- `profiling.c2NMethodCount.*`: If `profiling.delay` is not set, the agent will start profiling after the number of C2 compiled methods exceeds
a threshold:
  - `min`: Minimum number of C2 compiled methods. Default is 5 000.
  - `jfrEvent.period`: Period of the JFR event that is used to determine the number of C2 compiled methods. Default is `60s`.
  - `jfrEvent.duration`: Duration to collect the JFR events. Default is `302s`.
  - `jfrEvent.pause`: Pause between collecting the JFR events. Default is `120s`.
  - `c2NMethodCount.maxWaitingTime`: Maximum waiting time for the number of C2 compiled methods to exceed the threshold. Default is `1h`.

- `profiling.methodSampling.*`: The agent samples methods that are executed to determine actively used compiled methods.
  - `jfrEvent.period`: Period of the JFR event that is used to sample the methods. Default is `11ms`.
  - `jfrEvent.duration`: Duration to collect the JFR events. Default is `90s`.
  - `jfrEvent.pause`: Pause between collecting the JFR events. Default is `8m`.
  - `maxTopMethods`: Maximum number of JIT compiled methods considered from the top of the call stack. Default is `1`. For example, let the call stack be `A->B->C`. With 2, we'll take `B` and `C`. With 1, we'll take only `C`.
  - `maxPauseScale`: Maximum factor the pause can be increased. When we profile and no changes are done to HotCodeHeap, we increase the pause between sampling. Any change to HotCodeHeap will reset the pause to the value of `jfrEvent.pause`. Default is `4`.

- `hotCodeHeapMethods.*`: We keep a set of actively used methods. The following options configure the set:
  - `minMethodFrequency`: Minimum frequency of a method to be considered actively used. The frequency is calculated as the number of samples divided by the total number of samples. The value is a double between 0 and 1. Default is `0.0001`. This means that a method has to be seen in 0.01% of the samples to be considered actively used, which is 1 out of 10000 samples.
  - `maxNotSeenInProfiles`: Maximum number of consecutive profiles a method has not been seen in to consider it not actively used. Default is `8`.
  - `minColdMethodsRatio`: Minimum ratio of cold methods to actively used methods. We remove cold methods from HotCodeHeap if the ratio exceeds the minimum ratio. Default is `0.1`. This means that we keep at least 10% of the methods in HotCodeHeap that are not actively used.
  - `minNewMethodsRatio`: Minimum ratio of new methods found in a profile to actively used methods. We add new methods to HotCodeHeap if the ratio exceeds the minimum ratio. Default is `0.05`.
  - `minFreeSpaceRatio`: Minimum ratio of free space in HotCodeHeap. Default is `0.1`. If HotCodeHeap runs out of space, we don't add new methods to HotCodeHeap. We remove all methods which have not seen at least once (`maxNotSeenInProfiles` ignored). We need to have free space if recompilations happen due to deoptimizations.
