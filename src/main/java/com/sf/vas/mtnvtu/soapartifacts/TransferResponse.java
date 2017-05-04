
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
 *         &lt;element name="sequence" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="statusId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="txRefId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="seqstatus" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="seqtxRefdId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="lasseq" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="prevBalance" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="currentBalance" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="dateTime" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="origMsisdn" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="destMsisdn" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="responseCode" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="responseMessage" type="{http://www.w3.org/2001/XMLSchema}string"/>
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
    "sequence",
    "statusId",
    "txRefId",
    "seqstatus",
    "seqtxRefdId",
    "lasseq",
    "prevBalance",
    "currentBalance",
    "dateTime",
    "origMsisdn",
    "destMsisdn",
    "responseCode",
    "responseMessage"
})
@XmlRootElement(name = "transferResponse")
public class TransferResponse {

    @XmlElement(required = true, nillable = true)
    protected String sequence;
    @XmlElement(required = true, nillable = true)
    protected String statusId;
    @XmlElement(required = true, nillable = true)
    protected String txRefId;
    @XmlElement(required = true, nillable = true)
    protected String seqstatus;
    @XmlElement(required = true, nillable = true)
    protected String seqtxRefdId;
    @XmlElement(required = true, nillable = true)
    protected String lasseq;
    @XmlElement(required = true, nillable = true)
    protected String prevBalance;
    @XmlElement(required = true, nillable = true)
    protected String currentBalance;
    @XmlElement(required = true, nillable = true)
    protected String dateTime;
    @XmlElement(required = true, nillable = true)
    protected String origMsisdn;
    @XmlElement(required = true, nillable = true)
    protected String destMsisdn;
    @XmlElement(required = true, nillable = true)
    protected String responseCode;
    @XmlElement(required = true, nillable = true)
    protected String responseMessage;

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
     * Gets the value of the statusId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getStatusId() {
        return statusId;
    }

    /**
     * Sets the value of the statusId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setStatusId(String value) {
        this.statusId = value;
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

    /**
     * Gets the value of the seqstatus property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSeqstatus() {
        return seqstatus;
    }

    /**
     * Sets the value of the seqstatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSeqstatus(String value) {
        this.seqstatus = value;
    }

    /**
     * Gets the value of the seqtxRefdId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSeqtxRefdId() {
        return seqtxRefdId;
    }

    /**
     * Sets the value of the seqtxRefdId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSeqtxRefdId(String value) {
        this.seqtxRefdId = value;
    }

    /**
     * Gets the value of the lasseq property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLasseq() {
        return lasseq;
    }

    /**
     * Sets the value of the lasseq property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLasseq(String value) {
        this.lasseq = value;
    }

    /**
     * Gets the value of the prevBalance property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPrevBalance() {
        return prevBalance;
    }

    /**
     * Sets the value of the prevBalance property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPrevBalance(String value) {
        this.prevBalance = value;
    }

    /**
     * Gets the value of the currentBalance property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCurrentBalance() {
        return currentBalance;
    }

    /**
     * Sets the value of the currentBalance property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCurrentBalance(String value) {
        this.currentBalance = value;
    }

    /**
     * Gets the value of the dateTime property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDateTime() {
        return dateTime;
    }

    /**
     * Sets the value of the dateTime property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDateTime(String value) {
        this.dateTime = value;
    }

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
     * Gets the value of the responseCode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getResponseCode() {
        return responseCode;
    }

    /**
     * Sets the value of the responseCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setResponseCode(String value) {
        this.responseCode = value;
    }

    /**
     * Gets the value of the responseMessage property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getResponseMessage() {
        return responseMessage;
    }

    /**
     * Sets the value of the responseMessage property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setResponseMessage(String value) {
        this.responseMessage = value;
    }

}
