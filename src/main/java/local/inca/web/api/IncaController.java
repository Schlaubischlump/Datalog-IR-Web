package local.inca.web.api;

import local.inca.web.api.request.IncaProgram;
import local.inca.web.api.response.ApiResult;
import local.inca.web.inca.IncaRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inca")
public class IncaController {
    private final IncaRuntimeService incaRuntimeService;

    public IncaController(IncaRuntimeService incaRuntimeService) {
        this.incaRuntimeService = incaRuntimeService;
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResult<?>> execute(@RequestBody IncaProgram prog) {
        try {
            return new ResponseEntity<>(ApiResult.success(incaRuntimeService.execute(prog)), HttpStatus.OK);
        } catch(Exception e) {
            return new ResponseEntity<>(ApiResult.failure(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/lowered")
    public ResponseEntity<ApiResult<?>> loweredCode(@RequestBody IncaProgram prog) {
        try {
            return new ResponseEntity<>(ApiResult.success(incaRuntimeService.lowered(prog)), HttpStatus.OK);
        } catch(Exception e) {
            return new ResponseEntity<>(ApiResult.failure(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/optimized")
    public ResponseEntity<ApiResult<?>> optimizedCode(@RequestBody IncaProgram prog) {
        try {
            return new ResponseEntity<>(ApiResult.success(incaRuntimeService.optimized(prog)), HttpStatus.OK);
        } catch(Exception e) {
            return new ResponseEntity<>(ApiResult.failure(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }
}
