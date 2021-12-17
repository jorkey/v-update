import React, {useState} from 'react';

import {
  useClientBuilderConfigQuery,
  useClientServicesQuery, useRemoveClientServiceConfigMutation, useSetClientBuilderConfigMutation,
} from "../../../../../generated/graphql";
import BuilderConfiguration from "../BuilderConfiguration";

interface ClientServicesManagerParams {
}

const ClientBuilderConfiguration: React.FC<ClientServicesManagerParams> = props => {
  const [error, setError] = useState<string>()

  const { data: builderConfig, refetch: getBuilderConfig } = useClientBuilderConfigQuery({
    onError(err) {
      setError('Query client services error ' + err.message)
    }
  })

  const { data: clientServices, refetch: getClientServices } = useClientServicesQuery({
    onError(err) {
      setError('Query client services error ' + err.message)
    }
  })

  const [ setClientBuilderConfig ] =
    useSetClientBuilderConfigMutation({
      onError(err) { setError('Set client builder config error ' + err.message) },
    })

  const [ removeServiceConfig ] =
    useRemoveClientServiceConfigMutation({
      onError(err) { setError('Remove client service config error ' + err.message) },
    })

  if (builderConfig?.clientBuilderConfig && clientServices?.clientServicesConfig) {
    return (<BuilderConfiguration
              title='Client Builder Configuration'
              builderConfig={builderConfig.clientBuilderConfig}
              services={clientServices.clientServicesConfig.map(s => s.service)}
              setBuilderConfig={(distribution =>
                setClientBuilderConfig({ variables: { distribution: distribution } })
                  .then(() => {
                    console.log('getBuilderConfig')
                    getBuilderConfig()}))}
              removeServiceConfig={(service) =>
                removeServiceConfig({ variables: { service } }).then(() => getClientServices()) }
              setError={(error) => setError(error)}
              error={error}
      />)
  } else {
    return null
  }
}

export default ClientBuilderConfiguration;