/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Max Weninger <max.weninger@windriver.com> - https://bugs.eclipse.org/bugs/show_bug.cgi?id=148898 
 *******************************************************************************/

package org.eclipse.ui.texteditor;


import java.util.ResourceBundle;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.text.IFindReplaceTarget;

import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;


/**
 * An action which opens a Find/Replace dialog.
 * The dialog while open, tracks the active workbench part
 * and retargets itself to the active find/replace target.
 * <p>
 * It can also be used without having an IWorkbenchPart e.g. for
 * dialogs or wizards by just providing a {@link Shell} and an {@link IFindReplaceTarget}.
 * </p>
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @see IFindReplaceTarget
 */
public class FindReplaceAction extends ResourceAction implements IUpdate {

	/**
	 * Represents the "global" find/replace dialog. It tracks the active
	 * part and retargets the find/replace dialog accordingly. The find/replace
	 * target is retrieved from the active part using
	 * <code>getAdapter(IFindReplaceTarget.class)</code>.
	 * <p>
	 * The stub has the same life cycle as the find/replace dialog.</p>
	 * <p>
	 * If no IWorkbenchPart is available a Shell must be provided
	 * In this case the IFindReplaceTarget will never change.</p>
	 */
	static class FindReplaceDialogStub implements IPartListener, DisposeListener {

		/** The workbench part */
		private IWorkbenchPart fPart;
		/** The previous workbench part */
		private IWorkbenchPart fPreviousPart;
		/** The previous find/replace target */
		private IFindReplaceTarget fPreviousTarget;

		/** The workbench window */
		private IWorkbenchWindow fWindow;
		/** The find/replace dialog */
		private FindReplaceDialog fDialog;

		/**
		 * Creates a new find/replace dialog accessor anchored at the given part site.
		 * @param site the part site
		 */
		public FindReplaceDialogStub(IWorkbenchPartSite site) {
			this(site.getShell());
			fWindow= site.getWorkbenchWindow();
			IPartService service= fWindow.getPartService();
			service.addPartListener(this);
			partActivated(service.getActivePart());
		}

		/**
		 * Creates a new find/replace dialog accessor either anchored at 
		 * the given part site or on a shell
		 * 
		 * @param shell the shell if no site is used
		 * @since 3.3
		 */
		public FindReplaceDialogStub(Shell shell) {
			fDialog= new FindReplaceDialog(shell);				
			fDialog.create();
			fDialog.getShell().addDisposeListener(this);
		}

		/**
		 * Returns the find/replace dialog.
		 * @return the find/replace dialog
		 */
		public FindReplaceDialog getDialog() {
			return fDialog;
		}

		/*
		 * @see IPartListener#partActivated(IWorkbenchPart)
		 */
		public void partActivated(IWorkbenchPart part) {

			IFindReplaceTarget target= part == null ? null : (IFindReplaceTarget) part.getAdapter(IFindReplaceTarget.class);
			fPreviousPart= fPart;
			fPart= target == null ? null : part;

			if (fPreviousTarget != target) {
				fPreviousTarget= target;
				if (fDialog != null) {
					boolean isEditable= false;
					if (fPart instanceof ITextEditorExtension2) {
						ITextEditorExtension2 extension= (ITextEditorExtension2) fPart;
						isEditable= extension.isEditorInputModifiable();
					} else if (target != null)
						isEditable= target.isEditable();
					fDialog.updateTarget(target, isEditable, false);
				}
			}
		}

		/*
		 * @see IPartListener#partClosed(IWorkbenchPart)
		 */
		public void partClosed(IWorkbenchPart part) {

			if (part == fPreviousPart) {
				fPreviousPart= null;
				fPreviousTarget= null;
			}

			if (part == fPart)
				partActivated(null);
		}

		/*
		 * @see DisposeListener#widgetDisposed(DisposeEvent)
		 */
		public void widgetDisposed(DisposeEvent event) {

			if (fgFindReplaceDialogStub == this)
				fgFindReplaceDialogStub= null;

			if(fgFindReplaceDialogStubShell == this)
				fgFindReplaceDialogStubShell= null;
			
			if (fWindow != null) {
				fWindow.getPartService().removePartListener(this);
				fWindow= null;
			}
			fDialog= null;
			fPart= null;
			fPreviousPart= null;
			fPreviousTarget= null;
		}

		/*
		 * @see IPartListener#partOpened(IWorkbenchPart)
		 */
		public void partOpened(IWorkbenchPart part) {}

		/*
		 * @see IPartListener#partDeactivated(IWorkbenchPart)
		 */
		public void partDeactivated(IWorkbenchPart part) {}

		/*
		 * @see IPartListener#partBroughtToTop(IWorkbenchPart)
		 */
		public void partBroughtToTop(IWorkbenchPart part) {}
		
		/**
		 * Checks if the dialogs shell is the same as the
		 * given <code>shell</code> and if not clears the stub
		 * and closes the dialog.
		 *
		 * @param shell the shell check
		 * @since 3.3
		 */
		public void checkShell(Shell shell) {
			if (fDialog != null && shell != fDialog.getParentShell()) {
				if (fgFindReplaceDialogStub == this)
					fgFindReplaceDialogStub= null;

				if(fgFindReplaceDialogStubShell == this)
					fgFindReplaceDialogStubShell= null;
				
				fDialog.close();
			}			
		}
	}


	/**
	 * Listener for disabling the dialog on shell close.
	 * <p>
	 * This stub is shared amongst <code>IWorkbenchPart</code>s.</p>
	 */
	private static FindReplaceDialogStub fgFindReplaceDialogStub;
	
	/** Listener for disabling the dialog on shell close.
	 * <p>
	 * This stub is shared amongst <code>Shell</code>s.</p>
	 * @since 3.3
	 */
	private static FindReplaceDialogStub fgFindReplaceDialogStubShell;

	/** The action's target */
	private IFindReplaceTarget fTarget;
	/** The part to use if the action is created with a part. */
	private IWorkbenchPart fWorkbenchPart;
	/** The workbench window */
	private IWorkbenchWindow fWorkbenchWindow;
	/**
	 * The shell to use if the action is created with a shell.
	 * @since 3.3
	 */
	private Shell fShell;

	/**
	 * Creates a new find/replace action for the given workbench part.
	 * <p>
	 * The action configures its visual representation from the given
	 * resource bundle.</p>
	 *
	 * @param bundle the resource bundle
	 * @param prefix a prefix to be prepended to the various resource keys
	 *   (described in <code>ResourceAction</code> constructor), or
	 *   <code>null</code> if none
	 * @param workbenchPart	 the workbench part
	 * @see ResourceAction#ResourceAction(ResourceBundle, String)
	 */
	public FindReplaceAction(ResourceBundle bundle, String prefix, IWorkbenchPart workbenchPart) {
		super(bundle, prefix);
		Assert.isLegal(workbenchPart != null);
		fWorkbenchPart= workbenchPart;
		update();
	}

	/**
	 * Creates a new find/replace action for the given target and shell.
	 * <p>
	 * This can be used without having an IWorkbenchPart e.g. for
	 * dialogs or wizards.</p>
	 * <p>
	 * The action configures its visual representation from the given
	 * resource bundle.</p>
	 *
	 * @param bundle the resource bundle
	 * @param prefix a prefix to be prepended to the various resource keys
	 *   (described in <code>ResourceAction</code> constructor), or
	 *   <code>null</code> if none
	 * @param target the IFindReplaceTarget to use
	 * @param shell the shell
	 * @see ResourceAction#ResourceAction(ResourceBundle, String)
	 * 
	 * @since 3.3
	 */
	public FindReplaceAction(ResourceBundle bundle, String prefix, Shell shell, IFindReplaceTarget target) {
		super(bundle, prefix);
		Assert.isLegal(target != null && shell != null);
		fTarget= target;
		fShell= shell;
 		update();
	}
	
	/**
	 * Creates a new find/replace action for the given workbench window.
	 * The action configures its visual representation from the given
	 * resource bundle.
	 *
	 * @param bundle the resource bundle
	 * @param prefix a prefix to be prepended to the various resource keys
	 *   (described in <code>ResourceAction</code> constructor), or
	 *   <code>null</code> if none
	 * @param workbenchWindow the workbench window
	 * @see ResourceAction#ResourceAction(ResourceBundle, String)
	 *
	 * @deprecated use FindReplaceAction(ResourceBundle, String, IWorkbenchPart) instead
	 */
	public FindReplaceAction(ResourceBundle bundle, String prefix, IWorkbenchWindow workbenchWindow) {
		super(bundle, prefix);
		fWorkbenchWindow= workbenchWindow;
		update();
	}
	
	/*
	 *	@see IAction#run()
	 */
	public void run() {
		if (fTarget == null)
			return;

		final FindReplaceDialog dialog;
		final boolean isEditable;
		
		if(fShell == null) {
			if (fgFindReplaceDialogStub != null) {
				Shell shell= fWorkbenchPart.getSite().getShell();
				fgFindReplaceDialogStub.checkShell(shell);
			}
			if (fgFindReplaceDialogStub == null)
				fgFindReplaceDialogStub= new FindReplaceDialogStub(fWorkbenchPart.getSite());

			if (fWorkbenchPart instanceof ITextEditorExtension2)
				isEditable= ((ITextEditorExtension2) fWorkbenchPart).isEditorInputModifiable();
			else
				isEditable= fTarget.isEditable();
				
			dialog= fgFindReplaceDialogStub.getDialog();

		} else {
			if (fgFindReplaceDialogStubShell != null) {
				fgFindReplaceDialogStubShell.checkShell(fShell);
			}
			if (fgFindReplaceDialogStubShell == null)
				fgFindReplaceDialogStubShell= new FindReplaceDialogStub(fShell);

			isEditable= fTarget.isEditable();
			dialog= fgFindReplaceDialogStubShell.getDialog();
		}
		
		dialog.updateTarget(fTarget, isEditable, true);
		dialog.open();					
	}

	/*
	 * @see IUpdate#update()
	 */
	public void update() {

		if(fShell == null){
			if (fWorkbenchPart == null && fWorkbenchWindow != null)
				fWorkbenchPart= fWorkbenchWindow.getPartService().getActivePart();

			if (fWorkbenchPart != null)
				fTarget= (IFindReplaceTarget) fWorkbenchPart.getAdapter(IFindReplaceTarget.class);
			else
				fTarget= null;
		}
		setEnabled(fTarget != null && fTarget.canPerformFind());
	}
}
