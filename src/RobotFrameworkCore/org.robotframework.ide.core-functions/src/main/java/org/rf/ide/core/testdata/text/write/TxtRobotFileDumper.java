/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.testdata.text.write;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.rf.ide.core.testdata.IRobotFileDumper;
import org.rf.ide.core.testdata.model.AModelElement;
import org.rf.ide.core.testdata.model.FilePosition;
import org.rf.ide.core.testdata.model.ModelType;
import org.rf.ide.core.testdata.model.RobotFile;
import org.rf.ide.core.testdata.model.RobotVersion;
import org.rf.ide.core.testdata.model.table.ARobotSectionTable;
import org.rf.ide.core.testdata.model.table.KeywordTable;
import org.rf.ide.core.testdata.model.table.RobotElementsComparatorWithPositionChangedPresave;
import org.rf.ide.core.testdata.model.table.SettingTable;
import org.rf.ide.core.testdata.model.table.SettingTableElementsComparator;
import org.rf.ide.core.testdata.model.table.TableHeader;
import org.rf.ide.core.testdata.model.table.TableHeaderComparator;
import org.rf.ide.core.testdata.model.table.TestCaseTable;
import org.rf.ide.core.testdata.model.table.VariableTable;
import org.rf.ide.core.testdata.model.table.setting.DefaultTags;
import org.rf.ide.core.testdata.model.table.setting.ForceTags;
import org.rf.ide.core.testdata.model.table.setting.LibraryAlias;
import org.rf.ide.core.testdata.model.table.setting.LibraryImport;
import org.rf.ide.core.testdata.model.table.setting.Metadata;
import org.rf.ide.core.testdata.model.table.setting.ResourceImport;
import org.rf.ide.core.testdata.model.table.setting.SuiteDocumentation;
import org.rf.ide.core.testdata.model.table.setting.SuiteSetup;
import org.rf.ide.core.testdata.model.table.setting.SuiteTeardown;
import org.rf.ide.core.testdata.model.table.setting.TestSetup;
import org.rf.ide.core.testdata.model.table.setting.TestTeardown;
import org.rf.ide.core.testdata.model.table.setting.TestTemplate;
import org.rf.ide.core.testdata.model.table.setting.TestTimeout;
import org.rf.ide.core.testdata.model.table.setting.UnknownSetting;
import org.rf.ide.core.testdata.model.table.setting.VariablesImport;
import org.rf.ide.core.testdata.model.table.variables.AVariable;
import org.rf.ide.core.testdata.model.table.variables.DictionaryVariable;
import org.rf.ide.core.testdata.model.table.variables.DictionaryVariable.DictionaryKeyValuePair;
import org.rf.ide.core.testdata.model.table.variables.ListVariable;
import org.rf.ide.core.testdata.model.table.variables.ScalarVariable;
import org.rf.ide.core.testdata.model.table.variables.UnknownVariable;
import org.rf.ide.core.testdata.text.read.EndOfLineBuilder;
import org.rf.ide.core.testdata.text.read.EndOfLineBuilder.EndOfLineTypes;
import org.rf.ide.core.testdata.text.read.IRobotLineElement;
import org.rf.ide.core.testdata.text.read.IRobotTokenType;
import org.rf.ide.core.testdata.text.read.LineReader.Constant;
import org.rf.ide.core.testdata.text.read.RobotLine;
import org.rf.ide.core.testdata.text.read.RobotLine.PositionCheck;
import org.rf.ide.core.testdata.text.read.VersionAvailabilityInfo;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;
import org.rf.ide.core.testdata.text.read.separators.Separator;
import org.rf.ide.core.testdata.text.read.separators.Separator.SeparatorType;
import org.rf.ide.core.testdata.text.write.SectionBuilder.Section;
import org.rf.ide.core.testdata.text.write.SectionBuilder.SectionType;

import com.google.common.base.Optional;
import com.google.common.io.Files;

public class TxtRobotFileDumper implements IRobotFileDumper {

    private static final int NUMBER_OF_AFTER_UNIT_ELEMENTS_TO_TREAT_AS_NEW_UNIT_START = 3;

    private static final int MAX_NUMBER_OF_COLUMN_IN_LINE = 7;

    private static final int MAX_NUMBER_OF_CHARS_IN_LINE = 120;

    private static final String EMPTY = "\\";

    @Override
    public boolean canDumpFile(final File file) {
        boolean result = false;

        if (file != null && file.isFile()) {
            final String fileName = file.getName().toLowerCase();
            result = (fileName.endsWith(".txt") || fileName.endsWith(".robot"));
        }

        return result;
    }

    @Override
    public void dump(final File robotFile, final RobotFile model) throws Exception {
        Files.write(dump(model), robotFile, Charset.forName("utf-8"));
    }

    @Override
    public List<RobotLine> dumpToLines(final RobotFile model) {
        return newLines(model);
    }

    @Override
    public String dump(final List<RobotLine> lines) {
        final StringBuilder strLine = new StringBuilder();

        for (final RobotLine line : lines) {
            for (final IRobotLineElement elem : line.getLineElements()) {
                strLine.append(elem.getRaw());
            }

            strLine.append(line.getEndOfLine().getRaw());
        }

        return strLine.toString();
    }

    @Override
    public String dump(final RobotFile model) {
        final List<RobotLine> lines = dumpToLines(model);
        return dump(lines);
    }

    private List<RobotLine> newLines(final RobotFile model) {
        final List<RobotLine> lines = new ArrayList<>(0);

        final SectionBuilder sectionBuilder = new SectionBuilder();
        final List<Section> sections = sectionBuilder.build(model);

        dumpUntilRobotHeaderSection(model, sections, 0, lines);

        final SettingTable settingTable = model.getSettingTable();
        final List<AModelElement<SettingTable>> sortedSettings = sortSettings(settingTable);
        final VariableTable variableTable = model.getVariableTable();
        final List<AModelElement<VariableTable>> sortedVariables = sortVariables(variableTable);

        final TestCaseTable testCaseTable = model.getTestCaseTable();
        final KeywordTable keywordTable = model.getKeywordTable();

        final List<TableHeader<? extends ARobotSectionTable>> headers = new ArrayList<>(0);
        headers.addAll(settingTable.getHeaders());
        headers.addAll(variableTable.getHeaders());
        headers.addAll(testCaseTable.getHeaders());
        headers.addAll(keywordTable.getHeaders());
        Collections.sort(headers, new TableHeaderComparator());

        for (final TableHeader<? extends ARobotSectionTable> th : headers) {
            int sectionWithHeader = getSectionWithHeader(sections, th);

            if (th.getModelType() == ModelType.SETTINGS_TABLE_HEADER) {
                dumpSettingTable(model, sections, sectionWithHeader, th, sortedSettings, lines);
            } else if (th.getModelType() == ModelType.VARIABLES_TABLE_HEADER) {
                dumpVariableTable(model, sections, sectionWithHeader, th, sortedVariables, lines);
            } else if (th.getModelType() == ModelType.TEST_CASE_TABLE_HEADER) {
                dumpTestCaseTable(model, sections, sectionWithHeader, th, lines);
            } else if (th.getModelType() == ModelType.KEYWORDS_TABLE_HEADER) {
                dumpUserKeywordTable(model, sections, sectionWithHeader, th, lines);
            }

            if (sectionWithHeader > -1) {
                dumpUntilRobotHeaderSection(model, sections, sectionWithHeader + 1, lines);
            }
        }

        final List<Section> userSections = filterUserTableHeadersOnly(sections);
        dumpUntilRobotHeaderSection(model, userSections, 0, lines);

        addEOFinCaseIsMissing(model, lines);

        return lines;
    }

    private void dumpSettingTable(final RobotFile model, final List<Section> sections, final int sectionWithHeaderPos,
            final TableHeader<? extends ARobotSectionTable> th, final List<AModelElement<SettingTable>> sortedSettings,
            final List<RobotLine> lines) {
        dumpHeader(model, th, lines);

        if (!sortedSettings.isEmpty()) {
            final List<Section> settingSections = filterByType(sections, sectionWithHeaderPos, SectionType.SETTINGS);
            final int lastIndexToDump = getLastSortedToDump(settingSections, sortedSettings);
            for (int settingIndex = 0; settingIndex <= lastIndexToDump; settingIndex++) {
                if (!lines.isEmpty()) {
                    RobotLine lastLine = lines.get(lines.size() - 1);
                    IRobotLineElement endOfLine = lastLine.getEndOfLine();
                    if ((endOfLine == null || endOfLine.getFilePosition().isNotSet()
                            || endOfLine.getTypes().contains(EndOfLineTypes.NON)
                            || endOfLine.getTypes().contains(EndOfLineTypes.EOF))
                            && !lastLine.getLineElements().isEmpty()) {
                        final IRobotLineElement lineSeparator = getLineSeparator(model);
                        updateLine(model, lines, lineSeparator);
                    }
                }

                final AModelElement<SettingTable> suiteSetting = sortedSettings.get(settingIndex);
                final ModelType type = suiteSetting.getModelType();

                if (type == ModelType.SUITE_DOCUMENTATION) {
                    dumpSettingDocumentation(model, (SuiteDocumentation) suiteSetting, lines);
                } else if (type == ModelType.SUITE_SETUP) {
                    dumpSettingSuiteSetup(model, (SuiteSetup) suiteSetting, lines);
                } else if (type == ModelType.SUITE_TEARDOWN) {
                    dumpSettingSuiteTeardown(model, (SuiteTeardown) suiteSetting, lines);
                } else if (type == ModelType.SUITE_TEST_SETUP) {
                    dumpSettingSuiteTestSetup(model, (TestSetup) suiteSetting, lines);
                } else if (type == ModelType.SUITE_TEST_TEARDOWN) {
                    dumpSettingSuiteTestTeardown(model, (TestTeardown) suiteSetting, lines);
                } else if (type == ModelType.FORCE_TAGS_SETTING) {
                    dumpSettingForceTags(model, (ForceTags) suiteSetting, lines);
                } else if (type == ModelType.DEFAULT_TAGS_SETTING) {
                    dumpSettingDefaultTags(model, (DefaultTags) suiteSetting, lines);
                } else if (type == ModelType.SUITE_TEST_TEMPLATE) {
                    dumpSettingSuiteTestTemplate(model, (TestTemplate) suiteSetting, lines);
                } else if (type == ModelType.SUITE_TEST_TIMEOUT) {
                    dumpSettingSuiteTestTimeout(model, (TestTimeout) suiteSetting, lines);
                } else if (type == ModelType.METADATA_SETTING) {
                    dumpSettingMetadata(model, (Metadata) suiteSetting, lines);
                } else if (type == ModelType.LIBRARY_IMPORT_SETTING) {
                    dumpSettingLibraryImport(model, (LibraryImport) suiteSetting, lines);
                } else if (type == ModelType.RESOURCE_IMPORT_SETTING) {
                    dumpSettingResourceImport(model, (ResourceImport) suiteSetting, lines);
                } else if (type == ModelType.VARIABLES_IMPORT_SETTING) {
                    dumpSettingVariablesImport(model, (VariablesImport) suiteSetting, lines);
                } else {
                    // hope ModelType.SETTINGS_UNKNOWN
                    dumpSettingUnknown(model, (UnknownSetting) suiteSetting, lines);
                }
            }

            if (lastIndexToDump == sortedSettings.size() - 1) {
                sortedSettings.clear();
            } else {
                for (int settingIndex = 0; settingIndex <= lastIndexToDump; settingIndex++) {
                    sortedSettings.remove(0);
                }
            }
        }
    }

    private void dumpSettingUnknown(final RobotFile model, final UnknownSetting unknown, final List<RobotLine> lines) {

    }

    private void dumpSettingVariablesImport(final RobotFile model, final VariablesImport variables,
            final List<RobotLine> lines) {

    }

    private void dumpSettingResourceImport(final RobotFile model, final ResourceImport resource,
            final List<RobotLine> lines) {
        final RobotToken res = resource.getDeclaration();
        final FilePosition filePosition = res.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, res, lines);
        }

        IRobotLineElement lastToken = res;
        if (!res.isDirty() && currentLine != null) {
            updateLine(model, lines, res);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(res);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, res);
            lastToken = res;
        }

        List<RobotToken> resourcePaths = new ArrayList<>(0);
        if (resource.getPathOrName() != null) {
            resourcePaths.add(resource.getPathOrName());
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_RESOURCE_FILE_NAME, 1, resourcePaths);
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_RESOURCE_UNWANTED_ARGUMENT, 2,
                resource.getUnexpectedTrashArguments());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 3, resource.getComment());

        final List<RobotToken> tokens = new ArrayList<>();

        tokens.addAll(resourcePaths);
        tokens.addAll(resource.getUnexpectedTrashArguments());
        tokens.addAll(resource.getComment());

        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(res, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingLibraryImport(final RobotFile model, final LibraryImport library,
            final List<RobotLine> lines) {
        final RobotToken lib = library.getDeclaration();
        final FilePosition filePosition = lib.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, lib, lines);
        }

        IRobotLineElement lastToken = lib;
        if (!lib.isDirty() && currentLine != null) {
            updateLine(model, lines, lib);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(lib);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, lib);
            lastToken = lib;
        }

        List<RobotToken> libNames = new ArrayList<>(0);
        if (library.getPathOrName() != null) {
            libNames.add(library.getPathOrName());
        }

        List<RobotToken> libAliasDec = new ArrayList<>(0);
        if (library.getAlias() != null && library.getAlias().isPresent()) {
            libAliasDec.add(library.getAlias().getDeclaration());
        }

        List<RobotToken> libAliasNames = new ArrayList<>(0);
        if (!libAliasDec.isEmpty()) {
            LibraryAlias alias = library.getAlias();
            if (alias.isPresent() && alias.getLibraryAlias() != null) {
                libAliasNames.add(alias.getLibraryAlias());
            }
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_LIBRARY_NAME, 1, libNames);
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_LIBRARY_ARGUMENT, 2, library.getArguments());
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_LIBRARY_ALIAS, 3, libAliasDec);
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_LIBRARY_ALIAS_VALUE, 4, libAliasNames);
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 5, library.getComment());

        final List<RobotToken> tokens = new ArrayList<>();

        tokens.addAll(libNames);
        tokens.addAll(library.getArguments());
        tokens.addAll(libAliasDec);
        tokens.addAll(libAliasNames);
        tokens.addAll(library.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(lib, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingMetadata(final RobotFile model, final Metadata metadata, final List<RobotLine> lines) {
        final RobotToken suiteDec = metadata.getDeclaration();
        final FilePosition filePosition = suiteDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, suiteDec, lines);
        }

        IRobotLineElement lastToken = suiteDec;
        if (!suiteDec.isDirty() && currentLine != null) {
            updateLine(model, lines, suiteDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(suiteDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, suiteDec);
            lastToken = suiteDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        List<RobotToken> keys = new ArrayList<>();
        if (metadata.getKey() != null) {
            keys.add(metadata.getKey());
        }
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_METADATA_KEY, 1, keys);
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_METADATA_VALUE, 2, metadata.getValues());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 3, metadata.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(keys);
        tokens.addAll(metadata.getValues());
        tokens.addAll(metadata.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(suiteDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingSuiteTestTimeout(final RobotFile model, final TestTimeout testTimeout,
            final List<RobotLine> lines) {
        final RobotToken suiteDec = testTimeout.getDeclaration();
        final FilePosition filePosition = suiteDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, suiteDec, lines);
        }

        IRobotLineElement lastToken = suiteDec;
        if (!suiteDec.isDirty() && currentLine != null) {
            updateLine(model, lines, suiteDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(suiteDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, suiteDec);
            lastToken = suiteDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        List<RobotToken> keys = new ArrayList<>();
        if (testTimeout.getTimeout() != null) {
            keys.add(testTimeout.getTimeout());
        }
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_TEST_TIMEOUT_VALUE, 1, keys);
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_TEST_TIMEOUT_MESSAGE, 2,
                testTimeout.getMessageArguments());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 3, testTimeout.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(keys);
        tokens.addAll(testTimeout.getMessageArguments());
        tokens.addAll(testTimeout.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(suiteDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingSuiteTestTemplate(final RobotFile model, final TestTemplate testTemplate,
            final List<RobotLine> lines) {
        final RobotToken suiteDec = testTemplate.getDeclaration();
        final FilePosition filePosition = suiteDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, suiteDec, lines);
        }

        IRobotLineElement lastToken = suiteDec;
        if (!suiteDec.isDirty() && currentLine != null) {
            updateLine(model, lines, suiteDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(suiteDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, suiteDec);
            lastToken = suiteDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        List<RobotToken> keys = new ArrayList<>();
        if (testTemplate.getKeywordName() != null) {
            keys.add(testTemplate.getKeywordName());
        }
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_TEST_TEMPLATE_KEYWORD_NAME, 1, keys);
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_TEST_TEMPLATE_KEYWORD_UNWANTED_ARGUMENT, 2,
                testTemplate.getUnexpectedTrashArguments());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 3, testTemplate.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(keys);
        tokens.addAll(testTemplate.getUnexpectedTrashArguments());
        tokens.addAll(testTemplate.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(suiteDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingDefaultTags(final RobotFile model, final DefaultTags defaultTags,
            final List<RobotLine> lines) {
        final RobotToken suiteDec = defaultTags.getDeclaration();
        final FilePosition filePosition = suiteDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, suiteDec, lines);
        }

        IRobotLineElement lastToken = suiteDec;
        if (!suiteDec.isDirty() && currentLine != null) {
            updateLine(model, lines, suiteDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(suiteDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, suiteDec);
            lastToken = suiteDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_DEFAULT_TAG, 1, defaultTags.getTags());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 2, defaultTags.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(defaultTags.getTags());
        tokens.addAll(defaultTags.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(suiteDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingForceTags(final RobotFile model, final ForceTags forceTags, final List<RobotLine> lines) {
        final RobotToken suiteDec = forceTags.getDeclaration();
        final FilePosition filePosition = suiteDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, suiteDec, lines);
        }

        IRobotLineElement lastToken = suiteDec;
        if (!suiteDec.isDirty() && currentLine != null) {
            updateLine(model, lines, suiteDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(suiteDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, suiteDec);
            lastToken = suiteDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_FORCE_TAG, 1, forceTags.getTags());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 2, forceTags.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(forceTags.getTags());
        tokens.addAll(forceTags.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(suiteDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingSuiteTestTeardown(final RobotFile model, final TestTeardown testTeardown,
            final List<RobotLine> lines) {
        final RobotToken suiteDec = testTeardown.getDeclaration();
        final FilePosition filePosition = suiteDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, suiteDec, lines);
        }

        IRobotLineElement lastToken = suiteDec;
        if (!suiteDec.isDirty() && currentLine != null) {
            updateLine(model, lines, suiteDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(suiteDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, suiteDec);
            lastToken = suiteDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        List<RobotToken> keys = new ArrayList<>();
        if (testTeardown.getKeywordName() != null) {
            keys.add(testTeardown.getKeywordName());
        }
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_TEST_SETUP_KEYWORD_NAME, 1, keys);
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_TEST_SETUP_KEYWORD_ARGUMENT, 2,
                testTeardown.getArguments());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 3, testTeardown.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(keys);
        tokens.addAll(testTeardown.getArguments());
        tokens.addAll(testTeardown.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(suiteDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingSuiteTestSetup(final RobotFile model, final TestSetup testSetup,
            final List<RobotLine> lines) {
        final RobotToken suiteDec = testSetup.getDeclaration();
        final FilePosition filePosition = suiteDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, suiteDec, lines);
        }

        IRobotLineElement lastToken = suiteDec;
        if (!suiteDec.isDirty() && currentLine != null) {
            updateLine(model, lines, suiteDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(suiteDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, suiteDec);
            lastToken = suiteDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        List<RobotToken> keys = new ArrayList<>();
        if (testSetup.getKeywordName() != null) {
            keys.add(testSetup.getKeywordName());
        }
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_TEST_SETUP_KEYWORD_NAME, 1, keys);
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_TEST_SETUP_KEYWORD_ARGUMENT, 2,
                testSetup.getArguments());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 3, testSetup.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(keys);
        tokens.addAll(testSetup.getArguments());
        tokens.addAll(testSetup.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(suiteDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingSuiteTeardown(final RobotFile model, final SuiteTeardown suiteTeardown,
            final List<RobotLine> lines) {
        final RobotToken suiteDec = suiteTeardown.getDeclaration();
        final FilePosition filePosition = suiteDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, suiteDec, lines);
        }

        IRobotLineElement lastToken = suiteDec;
        if (!suiteDec.isDirty() && currentLine != null) {
            updateLine(model, lines, suiteDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(suiteDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, suiteDec);
            lastToken = suiteDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        List<RobotToken> keys = new ArrayList<>();
        if (suiteTeardown.getKeywordName() != null) {
            keys.add(suiteTeardown.getKeywordName());
        }
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_SUITE_TEARDOWN_KEYWORD_NAME, 1, keys);
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_SUITE_TEARDOWN_KEYWORD_ARGUMENT, 2,
                suiteTeardown.getArguments());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 3, suiteTeardown.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(keys);
        tokens.addAll(suiteTeardown.getArguments());
        tokens.addAll(suiteTeardown.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(suiteDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingSuiteSetup(final RobotFile model, final SuiteSetup suiteSetup,
            final List<RobotLine> lines) {
        final RobotToken suiteDec = suiteSetup.getDeclaration();
        final FilePosition filePosition = suiteDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, suiteDec, lines);
        }

        IRobotLineElement lastToken = suiteDec;
        if (!suiteDec.isDirty() && currentLine != null) {
            updateLine(model, lines, suiteDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(suiteDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, suiteDec);
            lastToken = suiteDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        List<RobotToken> keys = new ArrayList<>();
        if (suiteSetup.getKeywordName() != null) {
            keys.add(suiteSetup.getKeywordName());
        }
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_SUITE_SETUP_KEYWORD_NAME, 1, keys);
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_SUITE_SETUP_KEYWORD_ARGUMENT, 2,
                suiteSetup.getArguments());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 3, suiteSetup.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(keys);
        tokens.addAll(suiteSetup.getArguments());
        tokens.addAll(suiteSetup.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(suiteDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpSettingDocumentation(final RobotFile model, final SuiteDocumentation suiteDoc,
            final List<RobotLine> lines) {
        final RobotToken suiteDec = suiteDoc.getDeclaration();
        final FilePosition filePosition = suiteDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, suiteDec, lines);
        }

        IRobotLineElement lastToken = suiteDec;
        if (!suiteDec.isDirty() && currentLine != null) {
            updateLine(model, lines, suiteDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(suiteDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, suiteDec);
            lastToken = suiteDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        sorter.addPresaveSequenceForType(RobotTokenType.SETTING_DOCUMENTATION_TEXT, 1, suiteDoc.getDocumentationText());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 2, suiteDoc.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(suiteDoc.getDocumentationText());
        tokens.addAll(suiteDoc.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(suiteDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpVariableTable(final RobotFile model, final List<Section> sections, final int sectionWithHeaderPos,
            final TableHeader<? extends ARobotSectionTable> th,
            final List<AModelElement<VariableTable>> sortedVariables, final List<RobotLine> lines) {
        dumpHeader(model, th, lines);

        if (!sortedVariables.isEmpty()) {
            final List<Section> varSections = filterByType(sections, sectionWithHeaderPos, SectionType.VARIABLES);
            final int lastIndexToDump = getLastSortedToDump(varSections, sortedVariables);
            for (int varIndex = 0; varIndex <= lastIndexToDump; varIndex++) {
                if (!lines.isEmpty()) {
                    RobotLine lastLine = lines.get(lines.size() - 1);
                    IRobotLineElement endOfLine = lastLine.getEndOfLine();
                    if ((endOfLine == null || endOfLine.getFilePosition().isNotSet()
                            || endOfLine.getTypes().contains(EndOfLineTypes.NON)
                            || endOfLine.getTypes().contains(EndOfLineTypes.EOF))
                            && !lastLine.getLineElements().isEmpty()) {
                        final IRobotLineElement lineSeparator = getLineSeparator(model);
                        updateLine(model, lines, lineSeparator);
                    }
                }

                final AModelElement<VariableTable> var = sortedVariables.get(varIndex);
                final ModelType type = var.getModelType();

                if (type == ModelType.SCALAR_VARIABLE_DECLARATION_IN_TABLE) {
                    dumpScalarVariable(model, (ScalarVariable) var, lines);
                } else if (type == ModelType.LIST_VARIABLE_DECLARATION_IN_TABLE) {
                    dumpListVariable(model, (ListVariable) var, lines);
                } else if (type == ModelType.DICTIONARY_VARIABLE_DECLARATION_IN_TABLE) {
                    dumpDictionaryVariable(model, (DictionaryVariable) var, lines);
                } else {
                    dumpUnknownVariable(model, (UnknownVariable) var, lines);
                }
            }

            if (lastIndexToDump == sortedVariables.size() - 1) {
                sortedVariables.clear();
            } else {
                for (int varIndex = 0; varIndex <= lastIndexToDump; varIndex++) {
                    sortedVariables.remove(0);
                }
            }
        }
    }

    private void dumpScalarVariable(final RobotFile model, final ScalarVariable var, final List<RobotLine> lines) {
        final RobotToken varDec = var.getDeclaration();
        final FilePosition filePosition = varDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, varDec, lines);
        }

        IRobotLineElement lastToken = varDec;
        if (!varDec.isDirty() && currentLine != null) {
            updateLine(model, lines, varDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(varDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, varDec);
            lastToken = varDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        sorter.addPresaveSequenceForType(RobotTokenType.VARIABLES_VARIABLE_VALUE, 1, var.getValues());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 2, var.getComment());

        final List<RobotToken> tokens = new ArrayList<>();

        tokens.addAll(var.getValues());
        tokens.addAll(var.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(varDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpAsItIs(final RobotFile model, final IRobotLineElement startToken, final List<RobotToken> tokens,
            final List<RobotLine> lines) {
        final List<IRobotLineElement> dumps = new ArrayList<>(0);
        final int tokSize = tokens.size();
        int startOffset = startToken.getFilePosition().getOffset();

        RobotLine lastLine = null;
        IRobotLineElement lastToken = startToken;
        int meatTokens = 0;
        int offset = startOffset;

        int currentSize = dumps.size();
        boolean removeUpdated = false;

        if (offset == -1) {
            offset = tokens.get(0).getFilePosition().getOffset();
        }
        while (meatTokens < tokSize) {
            lastLine = model.getFileContent().get(model.getRobotLineIndexBy(offset).get());
            List<IRobotLineElement> lastToks = lastLine.getLineElements();
            final int lastToksSize = lastToks.size();

            final int elementPositionInLine;
            if (offset != startOffset) {
                elementPositionInLine = lastLine.getElementPositionInLine(offset, PositionCheck.STARTS).get();
            } else {
                elementPositionInLine = lastLine.getElementPositionInLine(lastToken).get() + 1;
            }
            currentSize = dumps.size();
            removeUpdated = false;
            for (int i = elementPositionInLine; i < lastToksSize; i++) {
                final IRobotLineElement e = lastToks.get(i);
                lastToken = e;
                if (e instanceof Separator) {
                    dumps.add(e);
                } else {
                    RobotToken rt = (RobotToken) e;
                    if (rt == tokens.get(meatTokens)) {
                        dumps.add(rt);
                        meatTokens++;
                    } else {
                        if (rt.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                                || rt.getTypes().contains(RobotTokenType.ASSIGNMENT)) {
                            dumps.add(rt);
                        } else if (isVariableContinue(dumps, rt)) {
                            dumps.add(rt);
                        } else if (startToken == rt) {
                            continue;
                        } else {
                            removeUpdated = true;
                            break;
                        }
                    }
                }
            }

            if (removeUpdated) {
                int dumpSize = dumps.size();
                if (!dumps.isEmpty()) {
                    for (int i = 0; i < dumpSize - currentSize; i++) {
                        dumps.remove(dumps.size() - 1);
                    }
                }
                meatTokens = tokSize;
                break;
            } else {
                lastToken = lastLine.getEndOfLine();
                dumps.add(lastLine.getEndOfLine());
                IRobotLineElement end = dumps.get(dumps.size() - 1);
                offset = end.getStartOffset() + (end.getEndColumn() - end.getStartColumn());
            }
        }

        if (lastLine != null && lastToken != null && !isEndOfLine(lastToken)) {
            final List<IRobotLineElement> lineElements = lastLine.getLineElements();
            final int size = lineElements.size();
            final int tokPosInLine = lastLine.getElementPositionInLine(lastToken).get();
            currentSize = dumps.size();
            removeUpdated = false;

            for (int index = tokPosInLine + 1; index < size; index++) {
                IRobotLineElement elem = lineElements.get(index);
                if (elem instanceof Separator) {
                    dumps.add(elem);
                } else {
                    removeUpdated = true;
                }
            }

            if (removeUpdated) {
                int dumpSize = dumps.size();
                if (!dumps.isEmpty()) {
                    for (int i = 0; i < dumpSize - currentSize; i++) {
                        dumps.remove(dumps.size() - 1);
                    }
                }
            } else {
                if (lastLine.getEndOfLine() != lastToken) {
                    dumps.add(lastLine.getEndOfLine());
                }
            }
        }

        for (final IRobotLineElement rle : dumps) {
            updateLine(model, lines, rle);
        }
    }

    private boolean isVariableContinue(final List<IRobotLineElement> dumps, final IRobotLineElement l) {
        boolean result = false;

        if (l.getTypes().contains(RobotTokenType.PREVIOUS_LINE_CONTINUE)) {
            if (dumps.isEmpty()) {
                result = true;
            } else {
                int dumpsSize = dumps.size();
                boolean sepsOnly = true;
                for (int dumpId = dumpsSize - 1; dumpId >= 0; dumpId--) {
                    final IRobotLineElement rle = dumps.get(dumpId);
                    if (rle instanceof Separator) {
                        continue;
                    } else if (isEndOfLine(rle)) {
                        break;
                    } else {
                        sepsOnly = false;
                        break;
                    }
                }

                result = sepsOnly;
            }
        }

        return result;
    }

    private void dumpListVariable(final RobotFile model, final ListVariable var, final List<RobotLine> lines) {
        final RobotToken varDec = var.getDeclaration();
        final FilePosition filePosition = varDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, varDec, lines);
        }

        IRobotLineElement lastToken = varDec;
        if (!varDec.isDirty() && currentLine != null) {
            updateLine(model, lines, varDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(varDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, varDec);
            lastToken = varDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        sorter.addPresaveSequenceForType(RobotTokenType.VARIABLES_VARIABLE_VALUE, 1, var.getItems());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 2, var.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(var.getItems());
        tokens.addAll(var.getComment());
        Collections.sort(tokens, sorter);

        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(varDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private void dumpDictionaryVariable(final RobotFile model, final DictionaryVariable var,
            final List<RobotLine> lines) {
        final RobotToken varDec = var.getDeclaration();
        final FilePosition filePosition = varDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, varDec, lines);
        }

        IRobotLineElement lastToken = varDec;
        if (!varDec.isDirty() && currentLine != null) {
            updateLine(model, lines, varDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(varDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, varDec);
            lastToken = varDec;
        }

        List<RobotToken> itemsAsValue = new ArrayList<>(0);
        for (final DictionaryKeyValuePair dv : var.getItems()) {
            RobotToken key = dv.getKey();
            if (!key.isDirty() && !dv.getValue().isDirty() && !dv.getRaw().getRaw().isEmpty()) {
                itemsAsValue.add(dv.getRaw());
            } else {
                RobotToken joinedKeyValue = new RobotToken();
                joinedKeyValue.setStartOffset(key.getStartOffset());
                joinedKeyValue.setLineNumber(key.getLineNumber());
                joinedKeyValue.setStartColumn(key.getStartColumn());
                joinedKeyValue.setRaw(key.getText() + "=" + dv.getValue().getText());
                joinedKeyValue.setText(joinedKeyValue.getRaw());

                itemsAsValue.add(joinedKeyValue);
            }
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        sorter.addPresaveSequenceForType(RobotTokenType.VARIABLES_VARIABLE_VALUE, 1, itemsAsValue);
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 2, var.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(itemsAsValue);
        tokens.addAll(var.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(varDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }

    }

    private void dumpUnknownVariable(final RobotFile model, final UnknownVariable var, final List<RobotLine> lines) {
        final RobotToken varDec = var.getDeclaration();
        final FilePosition filePosition = varDec.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, varDec, lines);
        }

        IRobotLineElement lastToken = varDec;
        if (!varDec.isDirty() && currentLine != null) {
            updateLine(model, lines, varDec);
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            final int tokenPosIndex = lineElements.indexOf(varDec);
            if (lineElements.size() - 1 > tokenPosIndex + 1) {
                for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
                    final IRobotLineElement nextElem = lineElements.get(index);
                    final List<IRobotTokenType> types = nextElem.getTypes();
                    if (types.contains(RobotTokenType.PRETTY_ALIGN_SPACE)
                            || types.contains(RobotTokenType.ASSIGNMENT)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    } else {
                        break;
                    }
                }
            }
        } else {
            updateLine(model, lines, varDec);
            lastToken = varDec;
        }

        RobotElementsComparatorWithPositionChangedPresave sorter = new RobotElementsComparatorWithPositionChangedPresave();
        sorter.addPresaveSequenceForType(RobotTokenType.VARIABLES_VARIABLE_VALUE, 1, var.getItems());
        sorter.addPresaveSequenceForType(RobotTokenType.START_HASH_COMMENT, 2, var.getComment());

        final List<RobotToken> tokens = new ArrayList<>();
        tokens.addAll(var.getItems());
        tokens.addAll(var.getComment());
        Collections.sort(tokens, sorter);
        // dump as it is
        if (!lastToken.getFilePosition().isNotSet() && !getFirstBrokenChainPosition(tokens, true).isPresent()
                && !tokens.isEmpty()) {
            dumpAsItIs(model, lastToken, tokens, lines);
            return;
        }

        int nrOfTokens = tokens.size();

        final List<Integer> lineEndPos = new ArrayList<>(getLineEndPos(varDec, tokens));
        if (nrOfTokens > 0) {
            boolean wasMyLine = false;
            for (int i = 0; i < nrOfTokens; i++) {
                final RobotToken robotToken = tokens.get(i);
                final FilePosition fp = robotToken.getFilePosition();
                if (!fp.isNotSet()) {
                    if (filePosition.getLine() == fp.getLine()) {
                        wasMyLine = true;
                        break;
                    }
                }
            }

            if (!wasMyLine && currentLine != null) {
                updateLine(model, lines, currentLine.getEndOfLine());
                if (!tokens.isEmpty()) {
                    Separator sep = getSeparator(model, lines, lastToken, tokens.get(0));
                    if (sep.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sep);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    updateLine(model, lines, sep);
                }
            }
        }

        for (int tokenId = 0; tokenId < nrOfTokens; tokenId++) {
            final IRobotLineElement tokElem = tokens.get(tokenId);
            Separator sep = getSeparator(model, lines, lastToken, tokElem);
            updateLine(model, lines, sep);
            lastToken = sep;

            updateLine(model, lines, tokElem);
            lastToken = tokElem;

            RobotLine currentLineTok = null;
            if (!tokElem.getFilePosition().isNotSet()) {
                currentLineTok = null;
                if (fileOffset >= 0) {
                    Optional<Integer> lineIndex = model.getRobotLineIndexBy(tokElem.getFilePosition().getOffset());
                    if (lineIndex.isPresent()) {
                        currentLineTok = model.getFileContent().get(lineIndex.get());
                    }
                }

                if (currentLineTok != null && !tokElem.isDirty()) {
                    List<IRobotLineElement> lineElements = currentLineTok.getLineElements();
                    int thisTokenPosIndex = lineElements.indexOf(tokElem);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            boolean dumpAfterSep = false;
            if (tokenId + 1 < nrOfTokens) {
                if (!tokElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                        && !tokElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                    IRobotLineElement nextElem = tokens.get(tokenId + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                            || nextElem.getTypes().contains(RobotTokenType.COMMENT_CONTINUE)) {
                        dumpAfterSep = true;
                    }
                }
            } else {
                dumpAfterSep = true;
            }

            if (dumpAfterSep && currentLine != null) {
                dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            }

            // sprawdzenie czy nie ma konca linii
            if (lineEndPos.contains(tokenId)) {
                if (currentLine != null) {
                    updateLine(model, lines, currentLine.getEndOfLine());
                } else {
                    // new end of line
                }

                if (!tokens.isEmpty() && tokenId + 1 < nrOfTokens) {
                    Separator sepNew = getSeparator(model, lines, lastToken, tokens.get(tokenId + 1));
                    if (sepNew.getTypes().contains(SeparatorType.PIPE)) {
                        updateLine(model, lines, sepNew);
                    }

                    RobotToken lineContinueToken = new RobotToken();
                    lineContinueToken.setRaw("...");
                    lineContinueToken.setText("...");
                    lineContinueToken.setType(RobotTokenType.PREVIOUS_LINE_CONTINUE);

                    updateLine(model, lines, lineContinueToken);

                    // updateLine(model, lines, sepNew);
                }
            }
        }
    }

    private Set<Integer> getLineEndPos(final IRobotLineElement currentDec,
            final List<? extends IRobotLineElement> elems) {
        final Set<Integer> lof = new HashSet<>();

        IRobotTokenType type = null;
        int size = elems.size();
        for (int index = 0; index < size; index++) {
            final IRobotLineElement el = elems.get(index);
            boolean isComment = el.getTypes().contains(RobotTokenType.START_HASH_COMMENT)
                    || el.getTypes().contains(RobotTokenType.COMMENT_CONTINUE);
            RobotTokenType newType = isComment ? RobotTokenType.START_HASH_COMMENT : RobotTokenType.UNKNOWN;

            if (type == null) {
                type = newType;
            } else {
                if (type != newType && !isComment) {
                    lof.add(index - 1);
                }

                type = newType;
            }
        }

        lof.add(size - 1);

        return lof;
    }

    private Optional<Integer> getFirstBrokenChainPosition(final List<? extends IRobotLineElement> elems,
            boolean treatNewAsBrokenChain) {
        Optional<Integer> o = Optional.absent();
        int size = elems.size();
        FilePosition pos = FilePosition.createNotSet();
        for (int index = 0; index < size; index++) {
            final FilePosition current = elems.get(index).getFilePosition();
            if (!current.isNotSet()) {
                if (!pos.isNotSet()) {
                    if (pos.isBefore(current)) {
                        pos = current;
                    } else {
                        o = Optional.of(index);
                        break;
                    }
                }
            } else {
                if (treatNewAsBrokenChain) {
                    o = Optional.of(index);
                    break;
                }
            }
        }
        return o;
    }

    public Separator getSeparator(final RobotFile model, final List<RobotLine> lines, final IRobotLineElement lastToken,
            final IRobotLineElement currentToken) {
        Separator sep = null;
        FilePosition fp = lastToken.getFilePosition();
        FilePosition fpTok = currentToken.getFilePosition();

        IRobotLineElement tokenToSearch = null;
        final int offset;
        if (fpTok.isNotSet()) {
            if (fp.isNotSet()) {
                tokenToSearch = lastToken;
                offset = -1;
            } else {
                tokenToSearch = lastToken;
                offset = fp.getOffset();
            }
        } else {
            tokenToSearch = currentToken;
            offset = fpTok.getOffset();
        }

        final RobotLine line;
        if (offset > -1) {
            line = model.getFileContent().get(model.getRobotLineIndexBy(offset).get());
        } else {
            if (!lines.isEmpty()) {
                line = lines.get(lines.size() - 1);
            } else {
                line = new RobotLine(0, model);
            }
        }

        final List<IRobotLineElement> elems = line.getLineElements();
        final Optional<Integer> tokenPos = line.getElementPositionInLine(tokenToSearch);
        if (tokenPos.isPresent()) {
            Integer tokPos = tokenPos.get();
            for (int index = tokPos - 1; index >= 0; index--) {
                IRobotLineElement elem = elems.get(index);
                if (elem instanceof RobotToken) {
                    break;
                } else if (elem instanceof Separator) {
                    sep = (Separator) elem;
                    break;
                } else {
                    continue;
                }
            }

            if (sep != null) {
                final Optional<SeparatorType> separatorForLine = line.getSeparatorForLine();
                if (separatorForLine.isPresent()) {
                    if (sep.getTypes().get(0) != separatorForLine.get()) {
                        if (separatorForLine.get() == SeparatorType.PIPE) {
                            List<Separator> seps = new ArrayList<>(0);
                            for (final IRobotLineElement e : elems) {
                                if (e instanceof Separator) {
                                    seps.add((Separator) e);
                                }
                            }

                            if (seps.size() > 1) {
                                sep = seps.get(seps.size() - 1);
                            } else {
                                sep = new Separator();
                                sep.setRaw(" | ");
                                sep.setText(" | ");
                                sep.setType(SeparatorType.PIPE);
                            }
                        }
                    }
                }
            }
        }

        if (sep == null) {
            sep = new Separator();
            sep.setRaw("\t");
            sep.setText("\t");
            sep.setType(SeparatorType.TABULATOR_OR_DOUBLE_SPACE);
        }

        return sep;
    }

    public String getEmpty() {
        return EMPTY;
    }

    private <T extends ARobotSectionTable> int getLastSortedToDump(final List<Section> sections,
            final List<AModelElement<T>> sortedElements) {
        final int size = sortedElements.size();
        int index = size - 1;
        int nextFound = 0;
        int nextStartFoundIndex = -1;

        if (sections.size() >= 1) {
            final Section currentSection = sections.get(0);
            final Set<Integer> startPosForElements = new HashSet<>();
            final List<Section> subElements = currentSection.getSubSections();
            for (final Section elem : subElements) {
                startPosForElements.add(elem.getStart().getOffset());
            }

            final Set<Integer> nextStartPosForElements = new HashSet<>();
            if (sections.size() > 1) {
                final Section nextSection = sections.get(1);
                final List<Section> nextElements = nextSection.getSubSections();
                for (final Section elem : nextElements) {
                    nextStartPosForElements.add(elem.getStart().getOffset());
                }
            }

            for (int elemIndex = 0; elemIndex < size; elemIndex++) {
                final AModelElement<T> var = sortedElements.get(elemIndex);
                FilePosition pos = var.getBeginPosition();
                if (pos.isNotSet()) {
                    if (size == index || elemIndex - 1 == index) {
                        index = elemIndex;
                        nextFound = 0;
                        nextStartFoundIndex = -1;
                    }
                } else if (startPosForElements.contains(pos.getOffset())) {
                    index = elemIndex;
                    nextFound = 0;
                    nextStartFoundIndex = -1;
                } else {
                    if (nextStartPosForElements.contains(pos.getOffset())) {
                        if (nextStartFoundIndex == -1) {
                            nextStartFoundIndex = elemIndex;
                            nextFound++;
                        } else if (nextFound == NUMBER_OF_AFTER_UNIT_ELEMENTS_TO_TREAT_AS_NEW_UNIT_START) {
                            index = nextStartFoundIndex;
                            break;
                        }
                    } else {
                        nextFound = 0;
                        nextStartFoundIndex = -1;
                    }
                }
            }
        }

        return index;
    }

    private void dumpTestCaseTable(final RobotFile model, final List<Section> sections, final int sectionWithHeaderPos,
            final TableHeader<? extends ARobotSectionTable> th, final List<RobotLine> lines) {
        dumpHeader(model, th, lines);
    }

    private void dumpUserKeywordTable(final RobotFile model, final List<Section> sections,
            final int sectionWithHeaderPos, final TableHeader<? extends ARobotSectionTable> th,
            final List<RobotLine> lines) {
        dumpHeader(model, th, lines);
    }

    private List<Section> filterByType(final List<Section> sections, final int sectionWithHeaderPos,
            final SectionType type) {
        List<Section> matched = new ArrayList<>();
        int sectionsSize = sections.size();
        for (int sectionId = sectionWithHeaderPos; sectionId < sectionsSize; sectionId++) {
            final Section section = sections.get(sectionId);
            if (section.getType() == type) {
                matched.add(section);
            }
        }

        return matched;
    }

    private void dumpHeader(final RobotFile model, final TableHeader<? extends ARobotSectionTable> th,
            final List<RobotLine> lines) {
        if (!lines.isEmpty()) {
            RobotLine lastLine = lines.get(lines.size() - 1);
            IRobotLineElement endOfLine = lastLine.getEndOfLine();
            if ((endOfLine == null || endOfLine.getFilePosition().isNotSet()
                    || endOfLine.getTypes().contains(EndOfLineTypes.NON)
                    || endOfLine.getTypes().contains(EndOfLineTypes.EOF)) && !lastLine.getLineElements().isEmpty()) {
                final IRobotLineElement lineSeparator = getLineSeparator(model);
                updateLine(model, lines, lineSeparator);
            }
        }

        final RobotToken decToken = th.getDeclaration();
        final FilePosition filePosition = decToken.getFilePosition();
        int fileOffset = -1;
        if (filePosition != null && !filePosition.isNotSet()) {
            fileOffset = filePosition.getOffset();
        }

        RobotLine currentLine = null;
        if (fileOffset >= 0) {
            Optional<Integer> lineIndex = model.getRobotLineIndexBy(fileOffset);
            if (lineIndex.isPresent()) {
                currentLine = model.getFileContent().get(lineIndex.get());
            }
        }

        boolean wasHeaderType = false;
        final RobotTokenType headerType = convertHeader(th.getModelType());
        final List<IRobotTokenType> tokenTypes = th.getDeclaration().getTypes();
        for (int index = 0; index < tokenTypes.size(); index++) {
            final IRobotTokenType tokenType = tokenTypes.get(index);
            if (RobotTokenType.isTableHeader(tokenType)) {
                if (headerType == tokenType) {
                    if (wasHeaderType) {
                        tokenTypes.remove(index);
                        index--;
                    }
                    wasHeaderType = true;
                } else {
                    tokenTypes.remove(index);
                    index--;
                }
            }
        }

        if (!wasHeaderType) {
            tokenTypes.clear();
            tokenTypes.add(headerType);
        }

        if ((decToken.getRaw() == null || decToken.getRaw().isEmpty())
                && (decToken.getText() == null || decToken.getText().isEmpty())) {
            final RobotVersion robotVersionInstalled = model.getParent().getRobotVersion();
            final VersionAvailabilityInfo vaiInCaseNoMatches = getTheMostCorrectOneRepresentation(headerType,
                    robotVersionInstalled);
            if (vaiInCaseNoMatches != null) {
                decToken.setRaw(vaiInCaseNoMatches.getRepresentation());
                decToken.setText(vaiInCaseNoMatches.getRepresentation());
            }
        } else if (decToken.getRaw() == null || decToken.getRaw().isEmpty()) {
            decToken.setRaw(decToken.getText());
        } else if (decToken.getText() == null || decToken.getText().isEmpty()) {
            decToken.setText(decToken.getRaw());
        }

        if (currentLine != null) {
            dumpSeparatorsBeforeToken(model, currentLine, decToken, lines);
        }

        updateLine(model, lines, decToken);
        IRobotLineElement lastToken = decToken;

        if (currentLine != null) {
            final List<IRobotLineElement> lineElements = currentLine.getLineElements();
            if (!decToken.isDirty()) {
                final int tokenPosIndex = lineElements.indexOf(decToken);
                if (lineElements.size() - 1 > tokenPosIndex + 1) {
                    final IRobotLineElement nextElem = lineElements.get(tokenPosIndex + 1);
                    if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                        updateLine(model, lines, nextElem);
                        lastToken = nextElem;
                    }
                }
            }

            for (final RobotToken columnToken : th.getColumnNames()) {
                dumpSeparatorsBeforeToken(model, currentLine, columnToken, lines);
                updateLine(model, lines, columnToken);
                lastToken = columnToken;
                if (!columnToken.isDirty()) {
                    int thisTokenPosIndex = lineElements.indexOf(decToken);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            for (final RobotToken commentPart : th.getComment()) {
                dumpSeparatorsBeforeToken(model, currentLine, commentPart, lines);
                updateLine(model, lines, commentPart);
                lastToken = commentPart;
                if (!commentPart.isDirty()) {
                    int thisTokenPosIndex = lineElements.indexOf(decToken);
                    if (thisTokenPosIndex >= 0) {
                        if (lineElements.size() - 1 > thisTokenPosIndex + 1) {
                            final IRobotLineElement nextElem = lineElements.get(thisTokenPosIndex + 1);
                            if (nextElem.getTypes().contains(RobotTokenType.PRETTY_ALIGN_SPACE)) {
                                updateLine(model, lines, nextElem);
                                lastToken = nextElem;
                            }
                        }
                    }
                }
            }

            dumpSeparatorsAfterToken(model, currentLine, lastToken, lines);
            final IRobotLineElement endOfLine = currentLine.getEndOfLine();
            if (endOfLine != null) {
                final List<IRobotTokenType> types = endOfLine.getTypes();
                if (!types.contains(EndOfLineTypes.EOF) && !types.contains(EndOfLineTypes.NON)) {
                    updateLine(model, lines, endOfLine);
                }
            }
        }
    }

    private IRobotLineElement getLineSeparator(final RobotFile model) {
        String eol = model.getParent().getFileLineSeparator();
        if (eol == null || eol.isEmpty()) {
            eol = System.lineSeparator();
        }

        RobotToken tempEOL = new RobotToken();
        tempEOL.setRaw(eol);
        tempEOL.setText(eol);

        return EndOfLineBuilder.newInstance().setEndOfLines(Constant.get(tempEOL)).buildEOL();
    }

    private void dumpSeparatorsAfterToken(final RobotFile model, final RobotLine currentLine,
            final IRobotLineElement currentToken, final List<RobotLine> lines) {
        int dumpEndIndex = -1;
        final List<IRobotLineElement> lineElements = currentLine.getLineElements();
        final int tokenPosIndex = lineElements.indexOf(currentToken);
        for (int index = tokenPosIndex + 1; index < lineElements.size(); index++) {
            if (lineElements.get(index) instanceof RobotToken) {
                break;
            } else {
                dumpEndIndex = index;
            }
        }

        if (dumpEndIndex >= 0) {
            for (int myIndex = tokenPosIndex + 1; myIndex < lineElements.size() && myIndex < dumpEndIndex; myIndex++) {
                updateLine(model, lines, lineElements.get(myIndex));
            }
        }
    }

    private void dumpSeparatorsBeforeToken(final RobotFile model, final RobotLine currentLine,
            final IRobotLineElement currentToken, final List<RobotLine> lines) {
        int dumpStartIndex = -1;
        final List<IRobotLineElement> lineElements = currentLine.getLineElements();
        final int tokenPosIndex = lineElements.indexOf(currentToken);
        for (int index = tokenPosIndex - 1; index >= 0; index--) {
            if (lineElements.get(index) instanceof RobotToken) {
                break;
            } else {
                dumpStartIndex = index;
            }
        }

        if (dumpStartIndex >= 0) {
            for (int myIndex = dumpStartIndex; myIndex < tokenPosIndex; myIndex++) {
                updateLine(model, lines, lineElements.get(myIndex));
            }
        }
    }

    private VersionAvailabilityInfo getTheMostCorrectOneRepresentation(final IRobotTokenType type,
            final RobotVersion robotVersionInstalled) {
        VersionAvailabilityInfo vaiInCaseNoMatches = null;
        for (final VersionAvailabilityInfo vai : type.getVersionAvailabilityInfos()) {
            if (vai.getRepresentation() == null) {
                continue;
            }
            if ((vai.getAvailableFrom() == null || robotVersionInstalled.isNewerOrEqualTo(vai.getAvailableFrom()))
                    && vai.getDepracatedFrom() == null && vai.getRemovedFrom() == null) {
                vaiInCaseNoMatches = vai;
                break;
            } else {
                if (vaiInCaseNoMatches == null) {
                    vaiInCaseNoMatches = vai;
                    continue;
                }

                if (vai.getAvailableFrom() == null || robotVersionInstalled.isNewerOrEqualTo(vai.getAvailableFrom())) {
                    if (vai.getRemovedFrom() == null) {
                        if (vaiInCaseNoMatches.getDepracatedFrom() != null
                                && vai.getDepracatedFrom().isNewerThan(vaiInCaseNoMatches.getDepracatedFrom())) {
                            vaiInCaseNoMatches = vai;
                        }
                    } else {
                        if (vaiInCaseNoMatches.getRemovedFrom() != null
                                && vai.getRemovedFrom().isNewerThan(vaiInCaseNoMatches.getRemovedFrom())) {
                            vaiInCaseNoMatches = vai;
                        }
                    }
                }
            }
        }

        return vaiInCaseNoMatches;
    }

    private RobotTokenType convertHeader(final ModelType modelType) {
        RobotTokenType type = RobotTokenType.UNKNOWN;
        if (modelType == ModelType.SETTINGS_TABLE_HEADER) {
            type = RobotTokenType.SETTINGS_TABLE_HEADER;
        } else if (modelType == ModelType.VARIABLES_TABLE_HEADER) {
            type = RobotTokenType.VARIABLES_TABLE_HEADER;
        } else if (modelType == ModelType.TEST_CASE_TABLE_HEADER) {
            type = RobotTokenType.TEST_CASES_TABLE_HEADER;
        } else if (modelType == ModelType.KEYWORDS_TABLE_HEADER) {
            type = RobotTokenType.KEYWORDS_TABLE_HEADER;
        }

        return type;
    }

    private List<AModelElement<VariableTable>> sortVariables(final VariableTable variableTable) {
        List<AModelElement<VariableTable>> list = new ArrayList<>();
        for (final AVariable var : variableTable.getVariables()) {
            list.add(var);
        }

        return list;
    }

    private List<AModelElement<SettingTable>> sortSettings(final SettingTable settingTable) {
        List<AModelElement<SettingTable>> list = new ArrayList<>();

        list.addAll(settingTable.getDefaultTags());
        list.addAll(settingTable.getDocumentation());
        list.addAll(settingTable.getForceTags());
        list.addAll(settingTable.getSuiteSetups());
        list.addAll(settingTable.getSuiteTeardowns());
        list.addAll(settingTable.getTestSetups());
        list.addAll(settingTable.getTestTeardowns());
        list.addAll(settingTable.getTestTemplates());
        list.addAll(settingTable.getTestTimeouts());
        list.addAll(settingTable.getUnknownSettings());

        list.addAll(settingTable.getMetadatas());
        list.addAll(settingTable.getImports());

        Collections.sort(list, new SettingTableElementsComparator());

        return list;
    }

    private int getSectionWithHeader(final List<Section> sections,
            final TableHeader<? extends ARobotSectionTable> theader) {
        int section = -1;
        final int sectionsSize = sections.size();
        for (int sectionId = 0; sectionId < sectionsSize; sectionId++) {
            final Section s = sections.get(sectionId);
            final FilePosition thPos = theader.getDeclaration().getFilePosition();
            if (thPos.isSamePlace(s.getStart()) || (thPos.isAfter(s.getStart()) && thPos.isBefore(s.getEnd()))) {
                section = sectionId;
                break;
            }
        }

        return section;
    }

    private List<Section> filterUserTableHeadersOnly(final List<Section> sections) {
        List<Section> userSections = new ArrayList<>(0);
        for (final Section section : sections) {
            SectionType type = section.getType();
            if (type == SectionType.TRASH || type == SectionType.USER_TABLE) {
                userSections.add(section);
            }
        }

        return userSections;
    }

    private void addEOFinCaseIsMissing(final RobotFile model, final List<RobotLine> lines) {
        IRobotLineElement buildEOL = new EndOfLineBuilder()
                .setEndOfLines(Arrays.asList(new Constant[] { Constant.EOF })).buildEOL();

        if (lines.isEmpty()) {
            updateLine(model, lines, buildEOL);
        } else {
            RobotLine robotLine = lines.get(lines.size() - 1);
            if (robotLine.getEndOfLine() == null || robotLine.getEndOfLine().getFilePosition().isNotSet()) {
                updateLine(model, lines, buildEOL);
            }
        }
    }

    private void dumpUntilRobotHeaderSection(final RobotFile model, final List<Section> sections,
            final int currentSection, final List<RobotLine> outLines) {
        int removedIndex = -1;

        int sectionSize = sections.size();
        for (int sectionId = currentSection; sectionId < sectionSize; sectionId++) {
            final Section section = sections.get(sectionId);
            SectionType type = section.getType();
            if (type == SectionType.TRASH || type == SectionType.USER_TABLE) {
                dumpFromTo(model, section.getStart(), section.getEnd(), outLines);
                removedIndex++;
            } else {
                break;
            }
        }

        for (int i = removedIndex; i > -1; i--) {
            sections.remove(currentSection);
        }
    }

    private void dumpFromTo(final RobotFile model, final FilePosition start, final FilePosition end,
            final List<RobotLine> outLines) {
        boolean meetEnd = false;

        final List<RobotLine> fileContent = model.getFileContent();
        for (final RobotLine line : fileContent) {
            for (final IRobotLineElement elem : line.getLineElements()) {
                final FilePosition elemPos = elem.getFilePosition();
                if (elemPos.isBefore(start)) {
                    continue;
                } else if (elemPos.isSamePlace(start) || elemPos.isSamePlace(end)
                        || (elemPos.isAfter(start) && elemPos.isBefore(end))) {
                    updateLine(model, outLines, elem);
                } else {
                    meetEnd = true;
                    break;
                }
            }

            if (meetEnd) {
                break;
            } else {
                final IRobotLineElement endOfLine = line.getEndOfLine();
                final FilePosition endOfLineFP = endOfLine.getFilePosition();
                if (endOfLineFP.isSamePlace(start) || endOfLineFP.isSamePlace(end)
                        || (endOfLineFP.isAfter(start) && endOfLineFP.isBefore(end))) {
                    updateLine(model, outLines, endOfLine);
                }
            }
        }
    }

    private void updateLine(final RobotFile model, final List<RobotLine> outLines, final IRobotLineElement elem) {
        if (isEndOfLine(elem)) {
            if (outLines.isEmpty()) {
                RobotLine line = new RobotLine(1, model);
                line.setEndOfLine(Constant.get(elem), 0, 0);
                outLines.add(line);
            } else {
                RobotLine line = outLines.get(outLines.size() - 1);
                final FilePosition pos = getPosition(line, outLines);
                line.setEndOfLine(Constant.get(elem), pos.getOffset(), pos.getColumn());
            }

            if (!elem.getTypes().contains(EndOfLineTypes.EOF)) {
                outLines.add(new RobotLine(outLines.size() + 1, model));
            }
        } else {
            final RobotLine line;
            if (outLines.isEmpty()) {
                line = new RobotLine(1, model);
                outLines.add(line);
            } else {
                line = outLines.get(outLines.size() - 1);
            }

            final IRobotLineElement artToken = cloneWithPositionRecalculate(elem, line, outLines);
            if (elem instanceof Separator) {
                if (line.getLineElements().isEmpty() && artToken.getTypes().contains(SeparatorType.PIPE)) {
                    Separator elemSep = (Separator) artToken;
                    int pipeIndex = elemSep.getRaw().indexOf('|');
                    if (pipeIndex >= 1 && !(pipeIndex == 1 && elemSep.getRaw().charAt(0) == ' ')) {
                        elemSep.setRaw(elemSep.getRaw().substring(pipeIndex));
                        elemSep.setText(elemSep.getRaw());
                    }
                }
            }

            // if (elem.getTypes().contains(RobotTokenType.VARIABLES_VARIABLE_VALUE) ||
            // elem.getTypes().contains(RobotTokenType.)) {
            if (elem instanceof RobotToken) {
                if (artToken.isDirty()) {
                    if (artToken.getRaw().isEmpty()) {
                        if (artToken instanceof RobotToken) {
                            RobotToken rt = (RobotToken) artToken;
                            rt.setRaw(getEmpty());
                            rt.setText(getEmpty());
                        }
                    } else {
                        if (artToken instanceof RobotToken) {
                            RobotToken rt = (RobotToken) artToken;
                            String text = formatWhiteSpace(rt.getText());
                            rt.setRaw(text);
                            rt.setText(text);
                        }
                    }
                }
            }
            // }

            line.addLineElement(cloneWithPositionRecalculate(artToken, line, outLines));
        }
    }

    private String formatWhiteSpace(final String text) {
        String result = text;
        StringBuilder str = new StringBuilder();
        char lastChar = (char) -1;
        if (text != null) {
            char[] cArray = text.toCharArray();
            int size = cArray.length;
            for (int cIndex = 0; cIndex < size; cIndex++) {
                char c = cArray[cIndex];
                if (cIndex == 0) {
                    if (c == ' ') {
                        str.append("\\ ");
                    } else {
                        str.append(c);
                    }
                } else if (cIndex + 1 == size) {
                    if (c == ' ') {
                        str.append("\\ ");
                    } else {
                        str.append(c);
                    }
                } else {
                    if (lastChar == ' ' && c == ' ') {
                        str.append("\\ ");
                    } else {
                        str.append(c);
                    }
                }

                lastChar = c;
            }

            result = str.toString();
        }

        return result;
    }

    private IRobotLineElement cloneWithPositionRecalculate(final IRobotLineElement elem, final RobotLine line,
            final List<RobotLine> outLines) {
        IRobotLineElement newElem;
        if (elem instanceof RobotToken) {
            RobotToken newToken = new RobotToken();
            newToken.setLineNumber(line.getLineNumber());
            if (elem.getRaw().isEmpty()) {
                newToken.setRaw(elem.getText());
            } else {
                newToken.setRaw(elem.getRaw());
            }
            newToken.setText(elem.getText());
            if (!elem.getTypes().isEmpty()) {
                newToken.getTypes().clear();
            }
            newToken.getTypes().addAll(elem.getTypes());
            FilePosition pos = getPosition(line, outLines);
            newToken.setStartColumn(pos.getColumn());
            newToken.setStartOffset(pos.getOffset());

            newElem = newToken;
        } else {
            Separator newSeparator = new Separator();
            newSeparator.setType((SeparatorType) elem.getTypes().get(0));
            newSeparator.setLineNumber(line.getLineNumber());
            if (elem.getRaw().isEmpty()) {
                newSeparator.setRaw(elem.getText());
            } else {
                newSeparator.setRaw(elem.getRaw());
            }
            newSeparator.setText(elem.getText());
            if (!elem.getTypes().isEmpty()) {
                newSeparator.getTypes().clear();
            }
            newSeparator.getTypes().addAll(elem.getTypes());
            FilePosition pos = getPosition(line, outLines);
            newSeparator.setStartColumn(pos.getColumn());
            newSeparator.setStartOffset(pos.getOffset());

            newElem = newSeparator;
        }

        return newElem;
    }

    private boolean isEndOfLine(final IRobotLineElement elem) {
        boolean result = false;
        for (final IRobotTokenType t : elem.getTypes()) {
            if (t instanceof EndOfLineTypes) {
                result = true;
                break;
            }
        }

        return result;
    }

    private FilePosition getPosition(final RobotLine line, final List<RobotLine> outLines) {
        return getPosition(line, outLines, 1);
    }

    private FilePosition getPosition(final RobotLine line, final List<RobotLine> outLines, int last) {
        FilePosition pos = FilePosition.createNotSet();

        final IRobotLineElement endOfLine = line.getEndOfLine();
        if (endOfLine != null && !endOfLine.getFilePosition().isNotSet()) {
            pos = calculateEndPosition(endOfLine, true);
        } else if (!line.getLineElements().isEmpty()) {
            pos = calculateEndPosition(line.getLineElements().get(line.getLineElements().size() - 1), false);
        } else if (outLines != null && !outLines.isEmpty() && outLines.size() - last >= 0) {
            pos = getPosition(outLines.get(outLines.size() - last), outLines, last + 1);
        } else {
            pos = new FilePosition(1, 0, 0);
        }

        return pos;
    }

    private FilePosition calculateEndPosition(final IRobotLineElement elem, boolean isEOL) {
        final FilePosition elemPos = elem.getFilePosition();

        final String raw = elem.getRaw();
        int rawLength = 0;
        if (raw != null) {
            rawLength = raw.length();
        }

        int textLength = 0;
        final String text = elem.getText();
        if (text != null) {
            textLength = text.length();
        }

        final int dataLength = Math.max(rawLength, textLength);

        return new FilePosition(elemPos.getLine(), isEOL ? 0 : elemPos.getColumn() + dataLength,
                elemPos.getOffset() + dataLength);
    }
}
