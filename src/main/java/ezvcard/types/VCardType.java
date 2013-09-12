package ezvcard.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import ezvcard.VCard;
import ezvcard.VCardDataType;
import ezvcard.VCardSubTypes;
import ezvcard.VCardVersion;
import ezvcard.io.CannotParseException;
import ezvcard.io.CompatibilityMode;
import ezvcard.io.EmbeddedVCardException;
import ezvcard.io.SkipMeException;
import ezvcard.util.HCardElement;
import ezvcard.util.JCardValue;
import ezvcard.util.VCardStringUtils;
import ezvcard.util.VCardStringUtils.JoinCallback;
import ezvcard.util.XCardElement;

/*
 Copyright (c) 2013, Michael Angstadt
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met: 

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer. 
 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution. 

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 The views and conclusions contained in the software and documentation are those
 of the authors and should not be interpreted as representing official policies, 
 either expressed or implied, of the FreeBSD Project.
 */

/**
 * Represents a vCard key/value pair entry (called a "type" or "property").
 * @author Michael Angstadt
 */
public abstract class VCardType implements Comparable<VCardType> {
	/**
	 * The name of the type.
	 */
	protected final String typeName;

	/**
	 * The group that this type belongs to or null if it doesn't belong to a
	 * group.
	 */
	protected String group;

	/**
	 * The list of attributes that are associated with this type (called
	 * "sub types" or "parameters").
	 */
	protected VCardSubTypes subTypes = new VCardSubTypes();

	/**
	 * Creates a vCard property.
	 * @param typeName the type name (e.g. "ADR")
	 */
	public VCardType(String typeName) {
		this.typeName = typeName;
	}

	/**
	 * Gets the name of this type.
	 * @return the type name (e.g. "ADR")
	 */
	public String getTypeName() {
		return typeName;
	}

	/**
	 * Determines if this property is supported by the given version.
	 * @param version the version
	 * @return true if it is supported, false if not
	 */
	public boolean isSupported(VCardVersion version) {
		for (VCardVersion v : getSupportedVersions()) {
			if (v == version) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the vCard versions that support this type.
	 * @return the vCard versions that support this type.
	 */
	public VCardVersion[] getSupportedVersions() {
		return VCardVersion.values();
	}

	/**
	 * Checks the property for data consistency problems or deviations from the
	 * spec. These problems will not prevent the property from being written to
	 * a data stream, but may prevent it from being parsed correctly by the
	 * consuming application. These problems can largely be avoided by reading
	 * the Javadocs of the property class, or by being familiar with the vCard
	 * standard.
	 * @param version the version to check the property against (use 4.0 for
	 * xCard and jCard)
	 * @param vcard the vCard this property belongs to
	 * @see VCard#validate
	 * @return a list of warnings or an empty list if no problems were found
	 */
	public final List<String> validate(VCardVersion version, VCard vcard) {
		List<String> warnings = new ArrayList<String>(0);

		//check the supported versions
		if (!isSupported(version)) {
			warnings.add("Property is not supported by version " + version.getVersion() + ".  Supported versions are: " + Arrays.toString(getSupportedVersions()));
		}

		//check parameters
		warnings.addAll(subTypes.validate(version));

		_validate(warnings, version, vcard);

		return warnings;
	}

	/**
	 * Checks the property for data consistency problems or deviations from the
	 * spec. Meant to be overridden by child classes that wish to provide
	 * validation logic.
	 * @param warnings the list to add the warnings to
	 * @param version the version to check the property against
	 * @param vcard the vCard this property belongs to
	 */
	protected void _validate(List<String> warnings, VCardVersion version, VCard vcard) {
		//empty
	}

	/**
	 * Converts this type object to a string for sending over the wire. It is
	 * NOT responsible for folding.
	 * @param version the version vCard that is being generated
	 * @param compatibilityMode allows the programmer to customize the
	 * marshalling process depending on the expected consumer of the vCard
	 * @return the string for sending over the wire
	 * @throws SkipMeException if this type should NOT be marshalled into the
	 * vCard
	 * @throws EmbeddedVCardException if the value of this type is an embedded
	 * vCard (i.e. the AGENT type)
	 */
	public final String marshalText(VCardVersion version, CompatibilityMode compatibilityMode) {
		StringBuilder sb = new StringBuilder();
		doMarshalText(sb, version, compatibilityMode);
		return sb.toString();
	}

	/**
	 * Converts this type object to a string for sending over the wire. It is
	 * NOT responsible for folding.
	 * @param value the buffer to add the marshalled value to
	 * @param version the version vCard that is being generated
	 * @param compatibilityMode allows the programmer to customize the
	 * marshalling process depending on the expected consumer of the vCard
	 * @throws SkipMeException if this type should NOT be marshalled into the
	 * vCard
	 * @throws EmbeddedVCardException if the value of this type is an embedded
	 * vCard (i.e. the AGENT type)
	 */
	protected abstract void doMarshalText(StringBuilder value, VCardVersion version, CompatibilityMode compatibilityMode);

	/**
	 * Marshals this type for inclusion in an xCard (XML document).
	 * @param parent the XML element that the type's value will be inserted
	 * into. For example, this would be the "&lt;fn&gt;" element for the "FN"
	 * type.
	 * @param version the version vCard that is being generated
	 * @param compatibilityMode allows the programmer to customize the
	 * marshalling process depending on the expected consumer of the vCard
	 * @throws SkipMeException if this type should NOT be marshalled into the
	 * vCard
	 */
	public final void marshalXml(Element parent, VCardVersion version, CompatibilityMode compatibilityMode) {
		XCardElement wrapper = new XCardElement(parent, version);
		doMarshalXml(wrapper, compatibilityMode);
	}

	/**
	 * Marshals this type for inclusion in an xCard (XML document). All child
	 * classes SHOULD override this, but are not required to.
	 * @param parent the XML element that the type's value will be inserted
	 * into. For example, this would be the "&lt;fn&gt;" element for the "FN"
	 * type.
	 * @param compatibilityMode allows the programmer to customize the
	 * marshalling process depending on the expected consumer of the vCard
	 * @throws SkipMeException if this type should NOT be marshalled into the
	 * vCard
	 */
	protected void doMarshalXml(XCardElement parent, CompatibilityMode compatibilityMode) {
		String value = marshalText(parent.version(), compatibilityMode);
		parent.append("unknown", value);
	}

	/**
	 * Marshals this type for inclusion in a jCard (JSON document).
	 * @param version the version vCard that is being generated
	 * @return the marshalled jCard value
	 * @throws SkipMeException if this type should NOT be marshalled into the
	 * vCard
	 */
	public final JCardValue marshalJson(VCardVersion version) {
		return doMarshalJson(version);
	}

	/**
	 * Marshals this type for inclusion in a jCard (JSON document).
	 * @param version the version vCard that is being generated
	 * @return the marshalled jCard value
	 * @throws SkipMeException if this type should NOT be marshalled into the
	 * vCard
	 */
	protected JCardValue doMarshalJson(VCardVersion version) {
		String valueStr = marshalText(version, CompatibilityMode.RFC);

		//determine the data type based on the VALUE parameter
		VCardDataType dataType = subTypes.getValue();
		return JCardValue.single(dataType, valueStr);
	}

	/**
	 * Gets the Sub Types to send over the wire.
	 * @param version the version vCard that is being generated
	 * @param compatibilityMode allows the programmer to customize the
	 * marshalling process depending on the expected consumer of the vCard
	 * @param vcard the vCard that is being marshalled
	 * @return the sub types that will be sent
	 */
	public final VCardSubTypes marshalSubTypes(VCardVersion version, CompatibilityMode compatibilityMode, VCard vcard) {
		VCardSubTypes copy = new VCardSubTypes(subTypes);
		doMarshalSubTypes(copy, version, compatibilityMode, vcard);
		return copy;
	}

	/**
	 * Gets the sub types that will be sent over the wire.
	 * 
	 * <p>
	 * If this method is NOT overridden, then the type's sub types will be sent
	 * over the wire as-is. In other words, whatever is in the
	 * {@link VCardType#subTypes} field will be sent. Child classes can override
	 * this method in order to modify the sub types before they are marshalled.
	 * </p>
	 * @param subTypes the sub types that will be marshalled into the vCard.
	 * This object is a copy of the {@link VCardType#subTypes} field, so any
	 * modifications done to this object will not effect the state of the field.
	 * @param version the version vCard that is being generated
	 * @param compatibilityMode allows the programmer to customize the
	 * marshalling process depending on the expected consumer of the vCard
	 * @param vcard the {@link VCard} object that is being marshalled
	 */
	protected void doMarshalSubTypes(VCardSubTypes subTypes, VCardVersion version, CompatibilityMode compatibilityMode, VCard vcard) {
		//do nothing
	}

	/**
	 * Unmarshals the type value from off the wire.
	 * @param subTypes the sub types that were parsed
	 * @param value the unfolded value from off the wire. If the wire value is
	 * in "quoted-printable" encoding, it will be decoded.
	 * @param version the version of the vCard that is being read
	 * @param warnings allows the programmer to alert the user to any
	 * note-worthy (but non-critical) issues that occurred during the
	 * unmarshalling process
	 * @param compatibilityMode allows the programmer to customize the
	 * unmarshalling process depending on where the vCard came from
	 * @throws SkipMeException if this type should NOT be added to the
	 * {@link VCard} object
	 * @throws CannotParseException if the property value could not be parsed
	 * @throws EmbeddedVCardException if the value of this type is an embedded
	 * vCard (i.e. the AGENT type)
	 */
	public final void unmarshalText(VCardSubTypes subTypes, String value, VCardVersion version, List<String> warnings, CompatibilityMode compatibilityMode) {
		this.subTypes = subTypes;
		doUnmarshalText(value, version, warnings, compatibilityMode);
	}

	/**
	 * Unmarshals the type value from off the wire.
	 * @param value the unfolded value from off the wire. If the wire value is
	 * in the "quoted-printable" encoding, it will be decoded.
	 * @param version the version of the vCard that is being read
	 * @param warnings allows the programmer to alert the user to any
	 * note-worthy (but non-critical) issues that occurred during the
	 * unmarshalling process
	 * @param compatibilityMode allows you to customize the unmarshalling
	 * process depending on where the vCard came from
	 * @throws SkipMeException if this type should NOT be added to the
	 * {@link VCard} object
	 * @throws CannotParseException if the property value could not be parsed
	 * @throws EmbeddedVCardException if the value of this type is an embedded
	 * vCard (i.e. the AGENT type)
	 */
	protected abstract void doUnmarshalText(String value, VCardVersion version, List<String> warnings, CompatibilityMode compatibilityMode);

	/**
	 * Unmarshals the type from an xCard (XML document).
	 * @param subTypes the sub types that were parsed
	 * @param element the XML element that contains the type data. For example,
	 * this would be the "&lt;fn&gt;" element for the "FN" type. This object
	 * will NOT include the "&lt;parameters&gt;" child element (it is removed
	 * after being unmarshalled into a {@link VCardSubTypes} object).
	 * @param version the version of the xCard
	 * @param warnings allows the programmer to alert the user to any
	 * note-worthy (but non-critical) issues that occurred during the
	 * unmarshalling process
	 * @param compatibilityMode allows the programmer to customize the
	 * unmarshalling process depending on where the vCard came from
	 * @throws SkipMeException if this type should NOT be added to the
	 * {@link VCard} object
	 * @throws CannotParseException if the property value could not be parsed
	 * @throws UnsupportedOperationException if the type class does not support
	 * xCard parsing
	 */
	public final void unmarshalXml(VCardSubTypes subTypes, Element element, VCardVersion version, List<String> warnings, CompatibilityMode compatibilityMode) {
		this.subTypes = subTypes;
		XCardElement wrapper = new XCardElement(element, version);
		doUnmarshalXml(wrapper, warnings, compatibilityMode);
	}

	/**
	 * Unmarshals the type from an xCard (XML document).
	 * @param element the XML element that contains the type data. For example,
	 * this would be the "&lt;fn&gt;" element for the "FN" type. This object
	 * will NOT include the "&lt;parameters&gt;" child element (it is removed
	 * after being unmarshalled into a {@link VCardSubTypes} object).
	 * @param warnings allows the programmer to alert the user to any
	 * note-worthy (but non-critical) issues that occurred during the
	 * unmarshalling process
	 * @param compatibilityMode allows the programmer to customize the
	 * unmarshalling process depending on where the vCard came from
	 * @throws SkipMeException if this type should NOT be added to the
	 * {@link VCard} object
	 * @throws CannotParseException if the property value could not be parsed
	 * @throws UnsupportedOperationException if the type class does not support
	 * xCard parsing
	 */
	protected void doUnmarshalXml(XCardElement element, List<String> warnings, CompatibilityMode compatibilityMode) {
		throw new UnsupportedOperationException("This type class does not support the parsing of xCards.");
	}

	/**
	 * Creates a {@link CannotParseException}, indicating that the XML elements
	 * that the parser expected to find are missing from the property's XML
	 * element.
	 * @param dataTypes the expected data types (null for "unknown")
	 */
	protected static CannotParseException missingXmlElements(VCardDataType... dataTypes) {
		String[] elements = new String[dataTypes.length];
		for (int i = 0; i < dataTypes.length; i++) {
			VCardDataType dataType = dataTypes[i];
			elements[i] = (dataType == null) ? "unknown" : dataType.getName().toLowerCase();
		}
		return missingXmlElements(elements);
	}

	/**
	 * Creates a {@link CannotParseException}, indicating that the XML elements
	 * that the parser expected to find are missing from property's XML element.
	 * @param elements the names of the expected XML elements.
	 */
	protected static CannotParseException missingXmlElements(String... elements) {
		String message;

		switch (elements.length) {
		case 0:
			message = "Property value empty.";
			break;
		case 1:
			message = "Property value empty (no <" + elements[0] + "> element found).";
			break;
		case 2:
			message = "Property value empty (no <" + elements[0] + "> or <" + elements[1] + "> elements found).";
			break;
		default:
			StringBuilder sb = new StringBuilder();

			sb.append("Property value empty (no ");
			VCardStringUtils.join(Arrays.asList(elements).subList(0, elements.length - 1), ", ", sb, new JoinCallback<String>() {
				public void handle(StringBuilder sb, String value) {
					sb.append('<').append(value).append('>');
				}
			});
			sb.append(", or <").append(elements[elements.length - 1]).append("> elements found).");

			message = sb.toString();
			break;
		}

		return new CannotParseException(message);
	}

	/**
	 * Unmarshals the type from an hCard (HTML document).
	 * @param element the HTML element that contains the type data.
	 * @param warnings allows the programmer to alert the user to any
	 * note-worthy (but non-critical) issues that occurred during the
	 * unmarshalling process
	 * @throws SkipMeException if this type should NOT be added to the
	 * {@link VCard} object
	 * @throws CannotParseException if the property value could not be parsed
	 * @throws EmbeddedVCardException if the value of this type is an embedded
	 * vCard (i.e. the AGENT type)
	 * @throws UnsupportedOperationException if the type class does not support
	 * hCard parsing
	 */
	public final void unmarshalHtml(org.jsoup.nodes.Element element, List<String> warnings) {
		HCardElement hcardElement = new HCardElement(element);
		doUnmarshalHtml(hcardElement, warnings);
	}

	/**
	 * Unmarshals the type from an hCard (HTML document).
	 * @param element the HTML element that contains the type data.
	 * @param warnings allows the programmer to alert the user to any
	 * note-worthy (but non-critical) issues that occurred during the
	 * unmarshalling process
	 * @throws SkipMeException if this type should NOT be added to the
	 * {@link VCard} object
	 * @throws CannotParseException if the property value could not be parsed
	 * @throws EmbeddedVCardException if the value of this type is an embedded
	 * vCard (i.e. the AGENT type)
	 */
	protected void doUnmarshalHtml(HCardElement element, List<String> warnings) {
		String value = element.value();
		doUnmarshalText(value, VCardVersion.V3_0, warnings, CompatibilityMode.RFC);
	}

	/**
	 * Unmarshals the type from a jCard (JSON).
	 * @param subTypes the sub types that were parsed
	 * @param value includes the data type and property value(s)
	 * @param version the version of the jCard that is being read
	 * @param warnings allows the programmer to alert the user to any
	 * note-worthy (but non-critical) issues that occurred during the
	 * unmarshalling process
	 * @throws SkipMeException if this type should NOT be added to the
	 * {@link VCard} object
	 * @throws CannotParseException if the property value could not be parsed
	 */
	public final void unmarshalJson(VCardSubTypes subTypes, JCardValue value, VCardVersion version, List<String> warnings) {
		this.subTypes = subTypes;
		doUnmarshalJson(value, version, warnings);
	}

	/**
	 * Unmarshals the type from a jCard (JSON).
	 * @param value includes the data type and property value(s)
	 * @param version the version of the jCard that is being read
	 * @param warnings allows the programmer to alert the user to any
	 * note-worthy (but non-critical) issues that occurred during the
	 * unmarshalling process
	 * @throws SkipMeException if this type should NOT be added to the
	 * {@link VCard} object
	 * @throws CannotParseException if the property value could not be parsed
	 */
	protected void doUnmarshalJson(JCardValue value, VCardVersion version, List<String> warnings) {
		doUnmarshalText(jcardValueToString(value), version, warnings, CompatibilityMode.RFC);
	}

	private String jcardValueToString(JCardValue value) {
		if (value.getValues().size() > 1) {
			List<String> multi = value.asMulti();
			if (multi != null) {
				return VCardStringUtils.join(multi, ",", new JoinCallback<String>() {
					public void handle(StringBuilder sb, String value) {
						sb.append(VCardStringUtils.escape(value));
					}
				});
			}
		}

		if (!value.getValues().isEmpty() && value.getValues().get(0).getArray() != null) {
			List<List<String>> structured = value.asStructured();
			if (structured != null) {
				return VCardStringUtils.join(structured, ";", new JoinCallback<List<String>>() {
					public void handle(StringBuilder sb, List<String> value) {
						VCardStringUtils.join(value, ",", sb, new JoinCallback<String>() {
							public void handle(StringBuilder sb, String value) {
								sb.append(VCardStringUtils.escape(value));
							}
						});
					}
				});
			}
		}

		return value.asSingle();
	}

	/**
	 * <p>
	 * Gets the qualified name (XML namespace and local part) for marshalling
	 * the type to an XML document (xCard).
	 * </p>
	 * <p>
	 * Extended type classes should override this method. By default, this
	 * method returns {@code null}, which instructs the marshallers to
	 * assign the following qualified name to the type:<br>
	 * <br>
	 * Namespace: xCard namespace<br>
	 * Local part: a lower-cased version of the type name
	 * </p>
	 * @return the XML qualified name or null to use the default qualified name
	 */
	public QName getQName() {
		return null;
	}

	/**
	 * Gets all of the property's parameters.
	 * @return the property's parameters
	 */
	public VCardSubTypes getSubTypes() {
		return subTypes;
	}

	/**
	 * Sets the property's parameters.
	 * @param subTypes the parameters
	 */
	public void setSubTypes(VCardSubTypes subTypes) {
		this.subTypes = subTypes;
	}

	/**
	 * Gets the first value of a parameter.
	 * @param name the parameter name (case insensitive, e.g. "LANGUAGE")
	 * @return the parameter value or null if not found
	 */
	public String getSubType(String name) {
		return subTypes.first(name);
	}

	/**
	 * Gets all values of a parameter.
	 * @param name the parameter name (case insensitive, e.g. "LANGUAGE")
	 * @return the parameter values
	 */
	public List<String> getSubTypes(String name) {
		return subTypes.get(name);
	}

	/**
	 * Replaces all existing values of a parameter with the given value.
	 * @param name the parameter name (case insensitive, e.g. "LANGUAGE")
	 * @param value the parameter value
	 */
	public void setSubType(String name, String value) {
		subTypes.replace(name, value);
	}

	/**
	 * Adds a value to a parameter.
	 * @param name the parameter name (case insensitive, e.g. "LANGUAGE")
	 * @param value the parameter value
	 */
	public void addSubType(String name, String value) {
		subTypes.put(name, value);
	}

	/**
	 * Removes a parameter from the property.
	 * @param name the parameter name (case insensitive, e.g. "LANGUAGE")
	 */
	public void removeSubType(String name) {
		subTypes.removeAll(name);
	}

	/**
	 * Gets this type's group.
	 * @return the group or null if it does not belong to a group
	 */
	public String getGroup() {
		return group;
	}

	/**
	 * Sets this type's group.
	 * @param group the group or null to remove the type's group
	 */
	public void setGroup(String group) {
		this.group = group;
	}

	/**
	 * Sorts by PREF parameter ascending. Types that do not have a PREF
	 * parameter are pushed to the end of the list.
	 */
	public int compareTo(VCardType that) {
		Integer pref0 = this.getSubTypes().getPref();
		Integer pref1 = that.getSubTypes().getPref();
		if (pref0 == null && pref1 == null) {
			return 0;
		}
		if (pref0 == null) {
			return 1;
		}
		if (pref1 == null) {
			return -1;
		}
		return pref1.compareTo(pref0);
	}

	//Note: The following parameter helper methods are package-scoped to prevent them from cluttering up the Javadocs

	/**
	 * <p>
	 * Gets all PID values.
	 * </p>
	 * <p>
	 * <b>Supported versions:</b> {@code 4.0}
	 * </p>
	 * @return the PID values or empty set if there are none
	 * @see VCardSubTypes#getPids
	 */
	List<Integer[]> getPids() {
		return subTypes.getPids();
	}

	/**
	 * <p>
	 * Adds a PID value.
	 * </p>
	 * <p>
	 * <b>Supported versions:</b> {@code 4.0}
	 * </p>
	 * @param localId the local ID
	 * @param clientPidMapRef the ID used to reference the property's globally
	 * unique identifier in the CLIENTPIDMAP property.
	 * @see VCardSubTypes#addPid(int, int)
	 */
	void addPid(int localId, int clientPidMapRef) {
		subTypes.addPid(localId, clientPidMapRef);
	}

	/**
	 * <p>
	 * Removes all PID values.
	 * </p>
	 * <p>
	 * <b>Supported versions:</b> {@code 4.0}
	 * </p>
	 * @see VCardSubTypes#removePids
	 */
	void removePids() {
		subTypes.removePids();
	}

	/**
	 * <p>
	 * Gets the preference value. The lower the number, the more preferred this
	 * property instance is compared with other properties in the same vCard of
	 * the same type. If a property doesn't have a preference value, then it is
	 * considered the least preferred.
	 * </p>
	 * <p>
	 * <b>Supported versions:</b> {@code 4.0}
	 * </p>
	 * @return the preference value or null if it doesn't exist
	 * @see VCardSubTypes#getPref
	 */
	Integer getPref() {
		return subTypes.getPref();
	}

	/**
	 * <p>
	 * Sets the preference value. The lower the number, the more preferred this
	 * property instance is compared with other properties in the same vCard of
	 * the same type. If a property doesn't have a preference value, then it is
	 * considered the least preferred.
	 * </p>
	 * <p>
	 * <b>Supported versions:</b> {@code 4.0}
	 * </p>
	 * @param pref the preference value or null to remove
	 * @see VCardSubTypes#setPref
	 */
	void setPref(Integer pref) {
		subTypes.setPref(pref);
	}

	/**
	 * Gets the language that the property value is written in.
	 * @return the language or null if not set
	 * @see VCardSubTypes#getLanguage
	 */
	String getLanguage() {
		return subTypes.getLanguage();
	}

	/**
	 * Sets the language that the property value is written in.
	 * @param language the language or null to remove
	 * @see VCardSubTypes#setLanguage
	 */
	void setLanguage(String language) {
		subTypes.setLanguage(language);
	}

	/**
	 * Gets the sorted position of this property when it is grouped together
	 * with other properties of the same type. Properties with low index values
	 * are put at the beginning of the sorted list and properties with high
	 * index values are put at the end of the list.
	 * @return the index or null if not set
	 * @see VCardSubTypes#setIndex
	 */
	Integer getIndex() {
		return subTypes.getIndex();
	}

	/**
	 * Sets the sorted position of this property when it is grouped together
	 * with other properties of the same type. Properties with low index values
	 * are put at the beginning of the sorted list and properties with high
	 * index values are put at the end of the list.
	 * @param index the index or null to remove
	 * @see VCardSubTypes#setIndex
	 */
	void setIndex(Integer index) {
		subTypes.setIndex(index);
	}
}