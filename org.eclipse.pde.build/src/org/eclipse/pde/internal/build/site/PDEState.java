package org.eclipse.pde.internal.build.site;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.jar.*;
import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.service.pluginconversion.PluginConverter;
import org.eclipse.osgi.service.resolver.*;
import org.eclipse.osgi.util.ManifestElement;
import org.eclipse.pde.internal.build.*;
import org.osgi.framework.*;
// This class provides a higher level API on the state
public class PDEState implements IPDEBuildConstants, IXMLConstants {
	static private BundleContext ctx;
	private StateObjectFactory factory;
	private State state;
	private long id;
	private Properties repositoryVersions;
	private ServiceReference logServiceReference;
	private ServiceReference converterServiceReference;
	private HashMap bundleClasspaths;
	protected long getNextId() {
		return ++id;
	}
	public PDEState() {
		factory = Platform.getPlatformAdmin().getFactory();
		state = factory.createState();
		state.setResolver(Platform.getPlatformAdmin().getResolver());
		id = 0;
		bundleClasspaths = new HashMap();
		loadPluginVersionFile();
	}
	public StateObjectFactory getFactory() {
		return factory;
	}
	public void addBundleDescription(BundleDescription toAdd) {
		state.addBundle(toAdd);
	}
	private FrameworkLog acquireFrameworkLogService() throws Exception {
		logServiceReference = ctx.getServiceReference(FrameworkLog.class.getName());
		if (logServiceReference == null)
			return null;
		return (FrameworkLog) ctx.getService(logServiceReference);
	}
	private PluginConverter acquirePluginConverter() throws Exception {
		converterServiceReference = ctx.getServiceReference(PluginConverter.class.getName());
		if (converterServiceReference == null)
			return null;
		return (PluginConverter) ctx.getService(converterServiceReference);
	}
	public boolean addBundle(Dictionary enhancedManifest, File bundleLocation) {
		updateVersionNumber(enhancedManifest);
		try {
			BundleDescription descriptor;
			descriptor = factory.createBundleDescription(enhancedManifest, bundleLocation.getAbsolutePath(), getNextId());
			bundleClasspaths.put(new Long(descriptor.getBundleId()), getClasspath(enhancedManifest));
			state.addBundle(descriptor);
		} catch (BundleException e) {
			//TODO Need to log
			return false;
		}
		return true;
	}
	private String[] getClasspath(Dictionary manifest) {
		String fullClasspath = (String) manifest.get(Constants.BUNDLE_CLASSPATH);
		String[] result = new String[0];
		try {
			if (fullClasspath != null) {
				ManifestElement[] classpathEntries;
				classpathEntries = ManifestElement.parseHeader(Constants.BUNDLE_CLASSPATH, fullClasspath);
				result = new String[classpathEntries.length];
				for (int i = 0; i < classpathEntries.length; i++) {
					result[i] = classpathEntries[i].getValue();
				}
			}
		} finally {
			return result;
		}
	}
	private void loadPluginVersionFile() {
		repositoryVersions = new Properties();
		FileInputStream input;
		try {
			input = new FileInputStream(AbstractScriptGenerator.getWorkingDirectory() + "/" + DEFAULT_PLUGIN_VERSION_FILENAME_DESCRIPTOR); //$NON-NLS-1$
			repositoryVersions.load(input);
		} catch (IOException e) {
			//Ignore
		}
	}
	private void updateVersionNumber(Dictionary manifest) {
		String q = (String) manifest.get(PROPERTY_QUALIFIER);
		if (q == null)
			return;
		String newQualifier = null;
		if (q.equalsIgnoreCase(PROPERTY_CONTEXT)) {
			newQualifier = (String) repositoryVersions.get(manifest.get(Constants.BUNDLE_SYMBOLICNAME));
			if (newQualifier == null)
				newQualifier = "" + Calendar.getInstance().get(Calendar.YEAR) + (Calendar.getInstance().get(Calendar.MONTH) + 1) + Calendar.getInstance().get(Calendar.DAY_OF_MONTH); //$NON-NLS-1$
		} else {
			newQualifier = q;
		}
		if (newQualifier == null)
			return;
		String oldVersion = (String) manifest.get(Constants.BUNDLE_VERSION);
		manifest.put(Constants.BUNDLE_VERSION, oldVersion.replaceFirst(PROPERTY_QUALIFIER, newQualifier));
	}
	public boolean addBundle(File bundleLocation) {
		Dictionary manifest;
		manifest = loadManifest(bundleLocation);
		if (manifest == null) {
			//TODO Log
			return false;
		}
		if (manifest.get(Constants.BUNDLE_SYMBOLICNAME).equals("org.eclipse.osgi")) {
			//TODO We need to handle the special case of the osgi bundle for
			// whom the bundle-classpath is specified in the eclipse.properties
			// file in the osgi folder
			manifest.put(Constants.BUNDLE_CLASSPATH, "core.jar, console.jar, osgi.jar, resolver.jar, defaultAdaptor.jar, eclipseAdaptor.jar");
		}
		return addBundle(manifest, bundleLocation);
	}
	
	private Dictionary loadManifest(File bundleLocation) {
		InputStream manifestStream = null;
		try {
			URL manifestLocation = null;
			if (bundleLocation.getName().endsWith("jar")) {
				manifestLocation = new URL("jar:file:" + bundleLocation + "!/" + JarFile.MANIFEST_NAME);
				manifestStream = manifestLocation.openStream();
			} else {
				manifestStream = new FileInputStream(new File(bundleLocation, JarFile.MANIFEST_NAME));
			}
		} catch (IOException e) {
			//ignore
		}
		if (manifestStream == null) {
			PluginConverter converter;
			try {
				converter = acquirePluginConverter();
				return converter.convertManifest(bundleLocation, false);
			} catch (Exception e1) {
				return null;
			}
		}
		try {
			Manifest m = new Manifest(manifestStream);
			return manifestToProperties(m.getMainAttributes());
		} catch (IOException e) {
			//TODO Log the exception
			return null;
		} finally {
			try {
				manifestStream.close();
			} catch (IOException e1) {
				//Ignore
			}
		}
	}
	private Properties manifestToProperties(Attributes d) {
		Iterator iter = d.keySet().iterator();
		Properties result = new Properties();
		while (iter.hasNext()) {
			Attributes.Name key = (Attributes.Name) iter.next();
			result.put(key.toString(), d.get(key));
		}
		return result;
	}
	public void addBundles(Collection bundles) {
		for (Iterator iter = bundles.iterator(); iter.hasNext();) {
			File bundle = (File) iter.next();
			addBundle(bundle);
		}
	}
	public void resolveState() {
		state.resolve(false);
	}
	public static void setCtx(BundleContext ctx) {
		PDEState.ctx = ctx;
	}
	public State getState() {
		return state;
	}
	public BundleDescription[] getDependentBundles(String bundleId, Version version) {
		BundleDescription root = state.getBundle(bundleId, version);
		return getDependentBundles(root);
	}
	/**
	 * This methods return the bundleDescriptions to which imports have been
	 * bound to.
	 * 
	 * @param bundleId
	 * @param version
	 * @return
	 */
	public static BundleDescription[] getImportedBundles(BundleDescription root) {
		if (root == null)
			return new BundleDescription[0];
		PackageSpecification[] packages = root.getPackages();
		ArrayList resolvedImported = new ArrayList(packages.length);
		for (int i = 0; i < packages.length; i++) {
			if (!packages[i].isExported() && packages[i].isResolved() && !resolvedImported.contains(packages[i].getSupplier()))
				resolvedImported.add(packages[i].getSupplier());
		}
		BundleDescription[] result = new BundleDescription[resolvedImported.size()];
		return (BundleDescription[]) resolvedImported.toArray(result);
	}
	/**
	 * This methods return the bundleDescriptions to which required bundles
	 * have been bound to.
	 * 
	 * @param bundleId
	 * @param version
	 * @return
	 */
	public static BundleDescription[] getRequiredBundles(BundleDescription root) {
		if (root == null)
			return new BundleDescription[0];
		BundleSpecification[] required = root.getRequiredBundles();
		ArrayList resolvedRequired = new ArrayList(required.length);
		for (int i = 0; i < required.length; i++) {
			if (required[i].isResolved() && !resolvedRequired.contains(required[i].getSupplier()))
				resolvedRequired.add(required[i].getSupplier());
		}
		BundleDescription[] result = new BundleDescription[resolvedRequired.size()];
		return (BundleDescription[]) resolvedRequired.toArray(result);
	}
	public BundleDescription getResolvedBundle(String bundleId, String version) {
		if (version == null)
			return getResolvedBundle(bundleId);
		BundleDescription description = getState().getBundle(bundleId, new Version(version));
		if (description.isResolved())
			return description;
		return null;
	}
	public BundleDescription getResolvedBundle(String bundleId) {
		BundleDescription[] description = getState().getBundles(bundleId);
		if (description == null)
			return null;
		for (int i = 0; i < description.length; i++) {
			if (description[i].isResolved())
				return description[i];
		}
		return null;
	}
	public static BundleDescription[] getDependentBundles(BundleDescription root) {
		BundleDescription[] imported = getImportedBundles(root);
		BundleDescription[] required = getRequiredBundles(root);
		BundleDescription[] dependents = new BundleDescription[imported.length + required.length];
		System.arraycopy(imported, 0, dependents, 0, imported.length);
		System.arraycopy(required, 0, dependents, imported.length, required.length);
		return dependents;
	}
	public static BundleDescription[] getDependentBundlesWithFragments(BundleDescription root) {
		BundleDescription[] imported = getImportedBundles(root);
		BundleDescription[] importedByFragments = getImportedByFragments(root);
		BundleDescription[] required = getRequiredBundles(root);
		BundleDescription[] requiredByFragments = getRequiredBundles(root);
		BundleDescription[] dependents = new BundleDescription[imported.length + importedByFragments.length + required.length + requiredByFragments.length];
		System.arraycopy(imported, 0, dependents, 0, imported.length);
		System.arraycopy(importedByFragments, 0, dependents, imported.length, importedByFragments.length);
		System.arraycopy(required, 0, dependents, imported.length + importedByFragments.length, required.length);
		System.arraycopy(requiredByFragments, 0, dependents, imported.length + importedByFragments.length + required.length, requiredByFragments.length);
		return dependents;
	}
	public static BundleDescription[] getImportedByFragments(BundleDescription root) {
		BundleDescription[] fragments = root.getFragments();
		List importedByFragments = new ArrayList();
		for (int i = 0; i < fragments.length; i++) {
			if (!fragments[i].isResolved())
				continue;
			merge(importedByFragments, getImportedBundles(fragments[i]));
		}
		BundleDescription[] result = new BundleDescription[importedByFragments.size()];
		return (BundleDescription[]) importedByFragments.toArray(result);
	}
	public static BundleDescription[] getRequiredByFragments(BundleDescription root) {
		BundleDescription[] fragments = root.getFragments();
		List importedByFragments = new ArrayList();
		for (int i = 0; i < fragments.length; i++) {
			if (!fragments[i].isResolved())
				continue;
			merge(importedByFragments, getRequiredBundles(fragments[i]));
		}
		BundleDescription[] result = new BundleDescription[importedByFragments.size()];
		return (BundleDescription[]) importedByFragments.toArray(result);
	}
	public static void merge(List source, BundleDescription[] toAdd) {
		for (int i = 0; i < toAdd.length; i++) {
			if (!source.contains(toAdd[i]))
				source.add(toAdd[i]);
		}
	}
	public Properties loadPropertyFileIn(Map toMerge, File location) {
		Properties result = new Properties();
		result.putAll(toMerge);
		InputStream propertyStream = null;
		try {
			propertyStream = new BufferedInputStream(new FileInputStream(new File(location, PROPERTIES_FILE)));
			result.load(propertyStream); //$NON-NLS-1$
		} catch (Exception e) {
			//ignore because compiled plug-ins do not have such files
		} finally {
			try {
				if (propertyStream != null)
					propertyStream.close();
			} catch (IOException e1) {
				//Ignore
			}
		}
		return result;
	}
	public HashMap getExtraData() {
		return bundleClasspaths;
	}
}