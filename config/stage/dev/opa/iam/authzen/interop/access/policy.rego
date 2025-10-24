package iam.authzen.interop.access

import rego.v1

default decision := {
	"decision": false,
	"context": {"record": []},
}

decision := result if {
	ids := [r.id | r := data.iam.authzen.interop.access.records[_]; can_access(r)]
	result := {
		"decision": count(ids) > 0,
		"context": {"record": ids},
	}
}

can_access(r) if {
	r.owner == input.subject.id
}
