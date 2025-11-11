package com.github.efeegbevwie.jsonsmith.services.fileSavers

import com.efe.jsonSmith.parser.languageParsers.ParsedType
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguage
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

fun saveGeneratedTypesToFiles(
    parsedType: ParsedType,
    targetLanguage: TargetLanguage,
    project: Project
): SaveFileResult {
    val descriptor = FileChooserDescriptor(
        false, true,
        false, false, false, false
    ).withTitle("Select Directory")
        .withDescription("Choose a directory to save the generated types.")

    val virtualFile: VirtualFile? = FileChooser.chooseFile(descriptor, project, null)
    val fileExtension = targetLanguage.targetLanguageConfig.fileExtension
    if (virtualFile == null) return SaveFileResult.Cancelled
    val directoryPath = VfsUtil.virtualToIoFile(virtualFile).absolutePath

    if (targetLanguage.targetLanguageConfig.saveClassesAsSeparateFiles) {
        val savedFiles = parsedType.parsedClasses
            .map { classEntries ->
                val className = classEntries.className
                val classBody = classEntries.classBody
                val fileName = "${className.ifEmpty { "JsonClass" }}$fileExtension"
                val content = buildString {
                    parsedType.imports?.let { appendLine(it) }
                    append("\n")
                    appendLine(classBody)
                }
                saveFile(
                    content = content,
                    directoryPath = directoryPath,
                    fileName = fileName,
                    project = project
                )
            }
        return if (savedFiles.allFileSaved()) {
            SaveFileResult.Success
        } else {
            SaveFileResult.Failure
        }
    } else {
        val fileName = "${parsedType.fileName.ifEmpty { "JsonClass" }}$fileExtension"
        val content: String = parsedType.stringRepresentation
        val fileSaved = saveFile(
            content = content,
            directoryPath = directoryPath,
            fileName = fileName,
            project = project
        )
        return when (fileSaved) {
            true -> SaveFileResult.Success
            false -> SaveFileResult.Failure
        }
    }

}

private fun saveFile(content: String, directoryPath: String, fileName: String, project: Project): Boolean {
    File(directoryPath, fileName).let {
        if (it.exists()) {
            it.delete()
        }
    }
    val targetFile = File(directoryPath, fileName)
    targetFile.writeText(content)
//    openFile(targetFile = targetFile, project = project)
    return targetFile.exists()
}

private fun openFile(targetFile: File, project: Project) {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile) ?: return
    fileEditorManager.openTextEditor(OpenFileDescriptor(project, virtualFile, 0), true)
}


private fun List<Boolean>.allFileSaved(): Boolean {
    for (item in this) {
        if (!item) {
            return false
        }
    }
    return true
}

enum class SaveFileResult {
    Success,
    Failure,
    Cancelled
}