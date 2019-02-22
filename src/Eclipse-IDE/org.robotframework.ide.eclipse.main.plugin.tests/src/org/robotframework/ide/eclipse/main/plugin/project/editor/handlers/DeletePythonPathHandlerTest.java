/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.project.editor.handlers;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.rf.ide.core.project.RobotProjectConfig;
import org.rf.ide.core.project.RobotProjectConfig.SearchPath;
import org.robotframework.ide.eclipse.main.plugin.project.RedProjectConfigEventData;
import org.robotframework.ide.eclipse.main.plugin.project.RobotProjectConfigEvents;
import org.robotframework.ide.eclipse.main.plugin.project.editor.RedProjectEditorInput;
import org.robotframework.ide.eclipse.main.plugin.project.editor.handlers.DeletePythonPathHandler.E4DeletePythonPathHandler;

public class DeletePythonPathHandlerTest {

    @Test
    public void whenSomePathsShouldBeRemoved_theyAreAndEventBrokerNotifiesAboutIt() {
        final E4DeletePythonPathHandler handler = new E4DeletePythonPathHandler();

        final SearchPath path1 = SearchPath.create("path1");
        final SearchPath path2 = SearchPath.create("path2");
        final SearchPath path3 = SearchPath.create("path3");
        final SearchPath path4 = SearchPath.create("path4");

        final RobotProjectConfig config = new RobotProjectConfig();
        config.addPythonPath(path1);
        config.addPythonPath(path2);
        config.addPythonPath(path3);
        config.addPythonPath(path4);

        final IFile file = mock(IFile.class);
        final RedProjectEditorInput input = mock(RedProjectEditorInput.class);
        when(input.getFile()).thenReturn(file);
        when(input.getProjectConfiguration()).thenReturn(config);

        final IEventBroker eventBroker = mock(IEventBroker.class);

        final IStructuredSelection selectedPaths = new StructuredSelection(newArrayList(path2, path4));
        handler.deleteSearchPaths(selectedPaths, input, eventBroker);

        verify(eventBroker).send(eq(RobotProjectConfigEvents.ROBOT_CONFIG_PYTHONPATH_STRUCTURE_CHANGED),
                argThat(hasCorrectEventData(file, config.getPythonPaths())));
        assertThat(config.getPythonPaths()).containsExactly(SearchPath.create("path1"), SearchPath.create("path3"));
    }

    @Test
    public void whenNoPathWasRemoved_eventBrokerDoesNotNotify() {
        final E4DeletePythonPathHandler handler = new E4DeletePythonPathHandler();

        final SearchPath path1 = SearchPath.create("path1");
        final SearchPath path2 = SearchPath.create("path2");

        final RobotProjectConfig config = new RobotProjectConfig();
        config.addPythonPath(path1);
        config.addPythonPath(path2);

        final RedProjectEditorInput input = mock(RedProjectEditorInput.class);
        when(input.getProjectConfiguration()).thenReturn(config);

        final IEventBroker eventBroker = mock(IEventBroker.class);

        final IStructuredSelection selectedPaths = new StructuredSelection(
                newArrayList(SearchPath.create("path3"), SearchPath.create("path4")));
        handler.deleteSearchPaths(selectedPaths, input, eventBroker);

        verifyZeroInteractions(eventBroker);
        assertThat(config.getPythonPaths()).containsExactly(SearchPath.create("path1"), SearchPath.create("path2"));
    }

    private static ArgumentMatcher<Object> hasCorrectEventData(final IFile file, final List<SearchPath> paths) {
        return object -> object instanceof RedProjectConfigEventData<?>
                && file.equals(((RedProjectConfigEventData<?>) object).getUnderlyingFile())
                && paths.equals(((RedProjectConfigEventData<?>) object).getChangedElement());
    }
}
