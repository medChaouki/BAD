package com.titaniumharmonics.bad.exercise

fun EditableExercise.compileForTest(): RuntimeExercise =
    when (val result = ExerciseCompiler.compile(this)) {
        is ExerciseCompilationResult.Success -> result.exercise
        is ExerciseCompilationResult.Failure -> error(
            result.validationErrors.joinToString("\n"),
        )
    }
