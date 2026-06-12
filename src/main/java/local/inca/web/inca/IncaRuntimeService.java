package local.inca.web.inca;

import inca.frontend.datalog.compile.CompiledDatalogUnit$;
import inca.frontend.datalog.compile.DatalogCompilerOptions;
import inca.frontend.datalog.executor.DatalogExecutor;
import inca.frontend.functional.compile.CompiledFunctionalUnit;
import inca.frontend.functional.compile.CompiledFunctionalUnit$;
import inca.frontend.functional.compile.FunctionalCompilerOptions;
import inca.frontend.functional.executor.FunctionalExecutor;
import inca.frontend.oodl.compile.CompiledOODLUnit$;
import inca.frontend.oodl.compile.OODLCompilerOptions;
import inca.frontend.oodl.executor.OODLExecutor;
import inca.ir.CompiledUnit;
import inca.ir.Module;
import inca.ir.execution.ThreadCount;
import inca.ir.visitors.BaseIRVisitor;
import inca.souffle.frontend.executor.SouffleExecutor;
import inca.util.compileroptions.CompilerOptions;
import inca.viatra.backend.Executor;
import local.inca.web.api.request.IncaProgram;
import local.inca.web.api.response.ExecutionResult;
import org.eclipse.viatra.query.runtime.rete.matcher.TimelyReteBackendFactory;
import org.springframework.stereotype.Service;
import scala.Function0;
import scala.collection.immutable.Seq;
import scala.jdk.javaapi.CollectionConverters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class IncaRuntimeService {
    private static final String DATALOG_OPTIONS = "compileroptions/Datalog.ini";
    private static final String FUNCTIONAL_OPTIONS = "compileroptions/Functional.ini";
    private static final String OODL_OPTIONS = "compileroptions/OODL.ini";

    @SafeVarargs
    private <T> Seq<T> seqOf(T... values) {
        return Seq.from(CollectionConverters.asScala(Arrays.asList(values)));
    }

    private <T> Seq<T> asSeq(List<T> list) {
        return Seq.from(CollectionConverters.asScala(list));
    }

    private CompiledUnit compile(IncaProgram prog) {
        var code = prog.code();

        var autoThreadCount = ThreadCount.fromOrdinal(0);
        var executor = switch (prog.backend()) {
            case Viatra      -> new Executor(TimelyReteBackendFactory.FIRST_ONLY_SEQUENTIAL);
            case Souffle     -> new inca.souffle.backend.Executor(autoThreadCount);
            case Ascent      -> new inca.ascent.backend.Executor(autoThreadCount);
            case DDLog       -> new inca.ddlog.backend.Executor();
            case Interpreter -> new inca.ir.execution.interpreter.Executor();
        };

        return switch (prog.dialect()) {
            case Datalog -> {
                var options = DatalogCompilerOptions.fromResource(DATALOG_OPTIONS);
                var exec = new DatalogExecutor(executor);
                yield exec.compileDatalog(code, options);
            }
            case FunctionalInca -> {
                var options = FunctionalCompilerOptions.fromResource(FUNCTIONAL_OPTIONS);
                var exec = new FunctionalExecutor(executor);
                var compiled = exec.compileFunction(code, options);
                compiled.setPipeline(CompiledFunctionalUnit$.MODULE$.pipeline());
                compiled.setOptimizationPipeline(CompiledFunctionalUnit$.MODULE$.optimizationPipeline());
                yield compiled;
            }
            case OODL -> {
                var options = OODLCompilerOptions.fromResource(OODL_OPTIONS);
                var exec = new OODLExecutor(executor);
                var compiled = exec.compileOODL(code, options);
                compiled.setPipeline(CompiledOODLUnit$.MODULE$.pipeline());
                compiled.setOptimizationPipeline(CompiledOODLUnit$.MODULE$.optimizationPipeline());
                yield compiled;
            }
            case Souffle -> {
                var defaultOptions = new CompilerOptions(seqOf());
                defaultOptions.setDefaults();
                var exec = new SouffleExecutor(executor);
                yield exec.compileSouffle(code, defaultOptions).mainUnit();
            }
        };
    }

    public String optimized(IncaProgram prog) {
        var s = new StringBuilder();
        for (Module mod: CollectionConverters.asJava(compile(prog).closed())) {
            s.append(mod.toString());
        };
        return s.toString();
    }

    public String lowered(IncaProgram prog) {
        var s = new StringBuilder();
        for (Module mod: CollectionConverters.asJava(compile(prog).lowered())) {
            s.append(mod.toString());
        };
        return s.toString();
    }

    public ExecutionResult execute(IncaProgram prog) {
        var code = prog.code();
        var query = prog.query();
        var relName = query.relName();
        var args = query.args();

        var autoThreadCount = ThreadCount.fromOrdinal(0);
        var executor = switch (prog.backend()) {
            case Viatra      -> new Executor(TimelyReteBackendFactory.FIRST_ONLY_SEQUENTIAL);
            case Souffle     -> new inca.souffle.backend.Executor(autoThreadCount);
            case Ascent      -> new inca.ascent.backend.Executor(autoThreadCount);
            case DDLog       -> new inca.ddlog.backend.Executor();
            case Interpreter -> new inca.ir.execution.interpreter.Executor();
        };

        var relation = switch (prog.dialect()) {
            case Datalog -> {
                var options = DatalogCompilerOptions.fromResource(DATALOG_OPTIONS);
                var exec = new DatalogExecutor(executor);
                var compiled = exec.compileDatalog(code, options);
                compiled.setPipeline(CompiledDatalogUnit$.MODULE$.pipeline());
                compiled.setOptimizationPipeline(CompiledDatalogUnit$.MODULE$.optimizationPipeline());
                var loaded = exec.loadDatalog(compiled);
                yield loaded.query(relName, seqOf(seqOf(args)));
            }
            case FunctionalInca -> {
                Optional<scala.collection.immutable.List<Function0<BaseIRVisitor>>> postProcessingPipeline = switch (prog.backend()) {
                    case Viatra -> Optional.of(CompiledFunctionalUnit$.MODULE$.viatraPostProcessingPipeline());
                    case DDLog  -> Optional.of(CompiledFunctionalUnit$.MODULE$.ddlogPostProcessingPipeline());
                    default     -> Optional.empty();
                };

                var options = FunctionalCompilerOptions.fromResource(FUNCTIONAL_OPTIONS);
                var exec = new FunctionalExecutor(executor);
                var compiled = exec.compileFunction(code, options);
                compiled.setPipeline(CompiledFunctionalUnit$.MODULE$.pipeline());
                compiled.setOptimizationPipeline(CompiledFunctionalUnit$.MODULE$.optimizationPipeline());
                postProcessingPipeline.ifPresent(compiled::setPostProcessingPipeline);
                var loaded = exec.loadFunction(compiled);
                yield loaded.execute(relName, seqOf(args));
            }
            case OODL -> {
                var options = OODLCompilerOptions.fromResource(OODL_OPTIONS);
                var exec = new OODLExecutor(executor);
                var compiled = exec.compileOODL(code, options);

                Optional<scala.collection.immutable.List<Function0<BaseIRVisitor>>> postProcessingPipeline = switch (prog.backend()) {
                    case Viatra -> Optional.of(compiled.viatraPostProcessingPipeline());
                    default     -> Optional.empty();
                };

                compiled.setPipeline(CompiledOODLUnit$.MODULE$.pipeline());
                compiled.setOptimizationPipeline(CompiledOODLUnit$.MODULE$.optimizationPipeline());
                postProcessingPipeline.ifPresent(compiled::setPostProcessingPipeline);
                var loaded = exec.loadOODL(compiled);
                yield loaded.execute(relName, seqOf(args));
            }
            case Souffle -> {
                var defaultOptions = new CompilerOptions(seqOf());
                defaultOptions.setDefaults();
                var exec = new SouffleExecutor(executor);
                var compiled = exec.compileSouffle(code, defaultOptions);
                var loaded = exec.loadSouffle(compiled);
                yield loaded.query(relName, seqOf(seqOf(args)));
            }
        };

        return toExecutionResult(relation);
    }

    private ExecutionResult toExecutionResult(inca.ir.execution.Relation relation) {
        var columns = new ArrayList<>(CollectionConverters.asJava(relation.parameterNames()));

        var rows = new ArrayList<List<Object>>();
        CollectionConverters.asJava(relation.matches()).forEach(match -> {
            var row = new ArrayList<>(CollectionConverters.asJava(match));
            rows.add(row);
        });

        return new ExecutionResult(
                relation.name(),
                relation.arity(),
                relation.size(),
                columns,
                rows
        );
    }
}

