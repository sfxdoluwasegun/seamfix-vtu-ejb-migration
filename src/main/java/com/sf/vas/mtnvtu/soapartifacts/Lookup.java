
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
 *         &lt;element name="destMsisdn" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="amount" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="tariffTypeId" type="{http://www.w3.org/2001/XMLSchema}string"/>
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
    "destMsisdn",
    "amount",
    "tariffTypeId"
})
@XmlRootElement(name = "lookup")
public class Lookup {

    @XmlElement(required = true, nillable = true)
    protected String origMsisdn;
    @XmlElement(required = true, nillable = true)
    protected String destMsisdn;
    @XmlElement(required = true, nillable = true)
    protected String amount;
    @XmlElement(required = true, nillable = true)
    protected String tariffTypeId;

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
     * Gets the value of the destMsisdn property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDestMsisdn() {
        return destMsisdn;
    }

    /**
     * Sets the value of the destMsisdn property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDestMsisdn(String value) {
        this.destMsisdn = value;
    }

    /**
     * Gets the value of the amount property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAmount() {
        return amount;
    }

    /**
     * Sets the value of the amount property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAmount(String value) {
        this.amount = value;
    }

    /**
     * Gets the value of the tariffTypeId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTariffTypeId() {
        return tariffTypeId;
    }

    /**
     * Sets the value of the tariffTypeId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTariffTypeId(String value) {
        this.tariffTypeId = value;
    }

}
