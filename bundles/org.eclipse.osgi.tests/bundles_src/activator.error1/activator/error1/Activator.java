/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package activator.error1;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	public Activator() {
		throw new RuntimeException();
	}

	public void start(BundleContext context) throws Exception {
		// TODO Auto-generated method stub

	}

	public void stop(BundleContext context) throws Exception {
		// TODO Auto-generated method stub

	}

}