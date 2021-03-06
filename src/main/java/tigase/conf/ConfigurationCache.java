/*
 * ConfigurationCache.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */



package tigase.conf;

//~--- non-JDK imports --------------------------------------------------------

import tigase.db.comp.RepositoryChangeListenerIfc;
import tigase.db.TigaseDBException;

import tigase.util.DataTypes;

//~--- JDK imports ------------------------------------------------------------

import java.io.FileWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Set;

/**
 * Created: Dec 10, 2009 2:02:41 PM
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class ConfigurationCache
				implements ConfigRepositoryIfc {
	/** Field description */
	public static final String CONFIG_DUMP_FILE_PROP_DEF = "etc/config-dump.properties";

	/** Field description */
	public static final String CONFIG_DUMP_FILE_PROP_KEY = "config-dump-file";

	/**
	 * Private logger for class instance.
	 */
	private static final Logger log = Logger.getLogger(ConfigurationCache.class.getName());

	//~--- fields ---------------------------------------------------------------

	/**
	 * Even though every element has a component name field the whole
	 * configuration is grouped by the component name anyway to improve
	 * access time to the configuration.
	 * Very rarely we need access to whole configuration, in most cases
	 * we access configuration for a particular server component.
	 */
	private Map<String, Set<ConfigItem>> config = new LinkedHashMap<String,
																									Set<ConfigItem>>();
	private String configDumpFileName                              =
		CONFIG_DUMP_FILE_PROP_DEF;
	private String hostname                                        = null;
	private RepositoryChangeListenerIfc<ConfigItem> repoChangeList = null;

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param repoChangeListener
	 */
	@Override
	public void addRepoChangeListener(
					RepositoryChangeListenerIfc<ConfigItem> repoChangeListener) {
		this.repoChangeList = repoChangeListener;
	}

	/**
	 * Method description
	 *
	 *
	 * @param repoChangeListener
	 */
	@Override
	public void removeRepoChangeListener(
					RepositoryChangeListenerIfc<ConfigItem> repoChangeListener) {
		this.repoChangeList = null;
	}

	/**
	 * Method description
	 *
	 *
	 * @param compName
	 * @param item
	 */
	public void addItem(String compName, ConfigItem item) {
		Set<ConfigItem> confItems = config.get(compName);

		if (confItems == null) {
			confItems = new LinkedHashSet<ConfigItem>();
			config.put(compName, confItems);
		}

		boolean updated = confItems.remove(item);

		confItems.add(item);
		if (repoChangeList != null) {
			if (updated) {
				repoChangeList.itemUpdated(item);
			} else {
				repoChangeList.itemAdded(item);
			}
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param item
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public void addItem(ConfigItem item) throws TigaseDBException {
		addItem(item.getCompName(), item);
	}

	/**
	 * Method description
	 *
	 *
	 * @param key
	 * @param value
	 *
	 * @throws ConfigurationException
	 */
	@Override
	public void addItem(String key, Object value) throws ConfigurationException {
		int idx1 = key.indexOf("/");

		if (idx1 > 0) {
			String compName = key.substring(0, idx1);
			int idx2        = key.lastIndexOf("/");
			String nodeName = null;
			String keyName  = key.substring(idx2 + 1);

			if (idx1 != idx2) {
				nodeName = key.substring(idx1 + 1, idx2);
			}

			ConfigItem item = getItemInstance();

			item.set(getDefHostname(), compName, nodeName, keyName, value);
			addItem(compName, item);
		} else {
			throw new IllegalArgumentException("You have to provide a key with at least" +
																				 " 'component_name/key_name': " + key);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public Collection<ConfigItem> allItems() throws TigaseDBException {
		List<ConfigItem> result = new ArrayList<ConfigItem>();

		for (Set<ConfigItem> items : config.values()) {
			result.addAll(items);
		}

		return result;
	}

	/**
	 * Method description
	 *
	 *
	 * @param key
	 *
	 * @return
	 */
	@Override
	public boolean contains(String key) {
		return getItem(key) != null;
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param compName
	 * @param node
	 * @param key
	 * @param def
	 *
	 * @return
	 */
	@Override
	public Object get(String compName, String node, String key, Object def) {
		ConfigItem item = getItem(compName, node, key);

		if (item != null) {
			return item.getConfigVal();
		}

		return def;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] getCompNames() {
		return config.keySet().toArray(new String[config.size()]);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public String getDefHostname() {
		return this.hostname;
	}

	/**
	 * Method description
	 *
	 *
	 * @param defs
	 * @param params
	 */
	@Override
	public void getDefaults(Map<String, Object> defs, Map<String, Object> params) {
		defs.put(CONFIG_DUMP_FILE_PROP_KEY, CONFIG_DUMP_FILE_PROP_DEF);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public Map<String, Object> getInitProperties() {
		return null;
	}

	/**
	 * Method description
	 *
	 *
	 * @param compName
	 * @param node
	 * @param key
	 *
	 * @return
	 */
	public ConfigItem getItem(String compName, String node, String key) {
		Set<ConfigItem> confItems = getItemsForComponent(compName);

		if (confItems != null) {
			for (ConfigItem item : confItems) {
				if (item.isNodeKey(node, key)) {
					return item;
				}
			}
		}

		return null;
	}

	/**
	 * Method description
	 *
	 *
	 * @param key
	 *
	 * @return
	 */
	@Override
	public ConfigItem getItem(String key) {
		int idx1 = key.indexOf("/");

		if (idx1 > 0) {
			String compName = key.substring(0, idx1);
			int idx2        = key.lastIndexOf("/");
			String nodeName = null;
			String keyName  = key.substring(idx2 + 1);

			if (idx1 != idx2) {
				nodeName = key.substring(idx1 + 1, idx2);
			}

			return getItem(compName, nodeName, keyName);
		} else {
			throw new IllegalArgumentException("You have to provide a key with at least" +
																				 " 'component_name/key_name': " + key);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public ConfigItem getItemInstance() {
		return new ConfigItem();
	}

	/**
	 * Method description
	 *
	 *
	 * @param compName
	 *
	 * @return
	 */
	@Override
	public Set<ConfigItem> getItemsForComponent(String compName) {
		return config.get(compName);
	}

	/**
	 * Method description
	 *
	 *
	 * @param compName
	 * @param node
	 *
	 * @return
	 */
	@Override
	public String[] getKeys(String compName, String node) {
		Set<String> keysForNode   = new LinkedHashSet<String>();
		Set<ConfigItem> confItems = config.get(compName);

		for (ConfigItem item : confItems) {
			if (item.isNode(node)) {
				keysForNode.add(item.getKeyName());
			}
		}
		if (keysForNode.size() > 0) {
			return keysForNode.toArray(new String[keysForNode.size()]);
		} else {
			return null;
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param compName
	 *
	 * @return
	 *
	 * @throws ConfigurationException
	 */
	@Override
	public Map<String, Object> getProperties(String compName)
					throws ConfigurationException {

		// It must not return a null value, even if configuration for the
		// component does not exist yet, it has to initialized to create new one.
		Map<String, Object> result = new LinkedHashMap<String, Object>();

		// Let's convert the internal representation of the configuration to that
		// used by the components.
		Set<ConfigItem> confItems = getItemsForComponent(compName);

		if (confItems != null) {
			for (ConfigItem item : confItems) {
				String key   = item.getConfigKey();
				Object value = item.getConfigVal();

				result.put(key, value);
			}
		}

		// Hopefuly this doesn't happen.... or I have a bug somewhere
		return result;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param params
	 *
	 * @throws ConfigurationException
	 */
	@Override
	public void init(Map<String, Object> params) throws ConfigurationException {}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public Iterator<ConfigItem> iterator() {
		try {
			Collection<ConfigItem> items = allItems();

			return (items != null)
						 ? items.iterator()
						 : null;
		} catch (TigaseDBException ex) {
			log.log(Level.WARNING, "Problem accessing repository: ", ex);

			return null;
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param compName
	 * @param props
	 *
	 * @throws ConfigurationException
	 */
	@Override
	public void putProperties(String compName, Map<String, Object> props)
					throws ConfigurationException {
		for (Map.Entry<String, Object> entry : props.entrySet()) {
			ConfigItem item = new ConfigItem();

			item.setNodeKey(getDefHostname(), compName, entry.getKey(), entry.getValue());
			addItem(compName, item);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public void reload() throws TigaseDBException {

		// Do nothing, this is in memory config repository only
	}

	/**
	 * Method description
	 *
	 *
	 * @param compName
	 * @param node
	 * @param key
	 */
	@Override
	public void remove(String compName, String node, String key) {
		ConfigItem item = getItem(compName, node, key);

		if (item != null) {
			removeItem(compName, item);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param compName
	 * @param item
	 */
	public void removeItem(String compName, ConfigItem item) {
		Set<ConfigItem> confItems = config.get(compName);

		if (confItems != null) {
			confItems.remove(item);
		}
		if (repoChangeList != null) {
			repoChangeList.itemRemoved(item);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param key
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public void removeItem(String key) throws TigaseDBException {
		ConfigItem item = getItem(key);

		if (item != null) {
			removeItem(item.getCompName(), item);
		}
	}

	//~--- set methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param compName
	 * @param node
	 * @param key
	 * @param value
	 */
	@Override
	public void set(String compName, String node, String key, Object value) {
		ConfigItem item = getItem(compName, node, key);

		if (item == null) {
			item = getItemInstance();
		}
		item.set(getDefHostname(), compName, node, key, value);
		addItem(compName, item);
	}

	/**
	 * Method description
	 *
	 *
	 * @param hostname
	 */
	@Override
	public void setDefHostname(String hostname) {
		this.hostname = hostname;
	}

	/**
	 * Method description
	 *
	 *
	 * @param properties
	 */
	@Override
	public void setProperties(Map<String, Object> properties) {
		configDumpFileName = (String) properties.get(CONFIG_DUMP_FILE_PROP_KEY);
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public int size() {
		int result = 0;

		for (Set<ConfigItem> items : config.values()) {
			result += items.size();
		}

		return result;
	}

	/**
	 * Method description
	 *
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public void store() throws TigaseDBException {
		if (!isOff(configDumpFileName)) {
			log.log(Level.WARNING, "Dumping server configuration to: {0}", configDumpFileName);
			try {
				FileWriter fw = new FileWriter(configDumpFileName, false);

				for (Map.Entry<String, Set<ConfigItem>> entry : config.entrySet()) {
					for (ConfigItem item : entry.getValue()) {
						fw.write(item.toPropertyString());
						fw.write("\n");
					}
				}
				fw.close();
			} catch (Exception e) {
				log.log(Level.WARNING, "Cannot dump server configuration.", e);
			}
		} else {
			log.log(Level.WARNING, "Dumping server configuration is OFF: {0}",
							configDumpFileName);
		}
	}

	//~--- get methods ----------------------------------------------------------

	private boolean isOff(String str) {
		return (str == null) || str.trim().isEmpty() || str.equalsIgnoreCase("off") ||
					 str.equalsIgnoreCase("none") || str.equalsIgnoreCase("false") ||
					 str.equalsIgnoreCase("no");
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param item
	 *
	 * @return
	 */
	@Override
	public String validateItem(ConfigItem item) {
		return null;
	}
}


//~ Formatted in Tigase Code Convention on 13/03/04
