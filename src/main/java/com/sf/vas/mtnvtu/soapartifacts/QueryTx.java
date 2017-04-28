
package com.sf.vas.mtnvtu.soapartifacts;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="origMsisdn" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="sequence" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="txRefId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "origMsisdn",
    "sequence",
    "txRefId"
})
@XmlRootElement(name = "queryTx")
public class QueryTx {

    @XmlElement(required = true, nillable = true)
    protected String origMsisdn;
    @XmlElement(required = true, nillable = true)
    protected String sequence;
    @XmlElement(required = true, nillable = true)
    protected String txRefId;

    /**
     * Gets the value of the origMsisdn property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOrigMsisdn() {
        return origMsisdn;
    }

    /**
     * Sets the value of the origMsisdn property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOrigMsisdn(String value) {
        this.origMsisdn = value;
    }

    /**
     * Gets the value of the sequence property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSequence() {
        return sequence;
    }

    /**
     * Sets the value of the sequence property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSequence(String value) {
        this.sequence = value;
    }

    /**
     * Gets the value of the txRefId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTxRefId() {
        return txRefId;
    }

    /**
     * Sets the value of the txRefId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTxRefId(String value) {
        this.txRefId = value;
    }

}
